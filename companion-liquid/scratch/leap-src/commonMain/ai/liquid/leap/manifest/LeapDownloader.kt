package ai.liquid.leap.manifest

import ai.liquid.leap.CpuThreadAdvisor
import ai.liquid.leap.LeapInferenceEngine
import ai.liquid.leap.LeapModelDownloadSha256MismatchException
import ai.liquid.leap.LeapModelDownloadSizeMismatchException
import ai.liquid.leap.LeapModelLoadingException
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.io.Sha256Source
import ai.liquid.leap.platform.ArtifactVersion
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Suppress("TooManyFunctions")
@OptIn(InternalLeapApi::class)
class LeapDownloader(
  val config: LeapDownloaderConfig = LeapDownloaderConfig(),
  private val httpClient: HttpClient? = null,
) : DownloaderEngine {
  companion object {
    private val logger: Logger = Logger.withTag("LeapDownloader")
    private val json = Json { prettyPrint = true }
  }

  /**
   * Runs [block] with an [HttpClient]. Uses the injected [httpClient] when available (no close);
   * otherwise creates a new client via [factory] and closes it when [block] returns.
   */
  internal inline fun <T> useClient(
    factory: () -> HttpClient = { defaultClientCache(json, config) },
    block: (HttpClient) -> T,
  ): T {
    if (httpClient != null) return block(httpClient)
    return factory().use(block)
  }

  override fun getResourceFolder(modelName: String, quantizationType: String): String =
    "${config.saveDir}/$modelName-$quantizationType"

  /** Creates the path of the file that should be saved to disk from the url of a resource */
  fun getPathFromUrl(
    url: String,
    manifestUrl: String,
    modelName: String,
    quantizationType: String,
  ): String {
    val resourceFolder =
      getResourceFolder(modelName = modelName, quantizationType = quantizationType)
    val path = url.fixRelativeUrl(manifestUrl)
    val filename = path.extractFilename()
    return "$resourceFolder/$filename"
  }

  // returns the file info needed to load a model
  override suspend fun getCachedManifest(modelName: String, quantizationType: String): Manifest? {
    if (!hasLocalFileSystem) return null
    val resourceFolder =
      getResourceFolder(modelName = modelName, quantizationType = quantizationType)
    logger.d("Checking for parent folder: $resourceFolder")

    val manifestFilename = manifestFilename(modelName, quantizationType)
    Path(resourceFolder, manifestFilename).let { schemaPath ->
      logger.d("Checking for schema path: $schemaPath")
      if (!SystemFileSystem.exists(schemaPath)) {
        return null
      }
      return withContext(ioDispatcher) {
        SystemFileSystem.source(schemaPath).buffered().use {
          val schemaContents = it.readString()
          if (schemaContents.isEmpty()) {
            logger.w { "Cached manifest is empty: $schemaPath" }
            return@withContext null
          }
          try {
            val manifest = json.decodeFromString<Manifest>(schemaContents)
            val absoluteSchemaPath = SystemFileSystem.resolve(schemaPath).toString()
            manifest.copy(pathOnDisk = absoluteSchemaPath)
          } catch (e: Exception) {
            logger.w(e) { "Failed to decode cached manifest: $schemaPath" }
            null
          }
        }
      }
    }
  }

  @Suppress("NestedBlockDepth")
  override suspend fun resolve(modelName: String, quantizationType: String): Manifest? {
    var result: Manifest?

    // Try cached manifest first
    result = getCachedManifest(modelName = modelName, quantizationType = quantizationType)

    if (result == null) {
      logger.d { "Resolving manifest for $modelName / $quantizationType" }
      val leapManifestUrl =
        manifestUrl(modelName, quantizationType, config.baseUrl ?: "https://leap.liquid.ai")

      useClient { client ->
        // Fetch manifest metadata to get the actual manifest URL
        val metaResponse =
          client
            .prepareGet(leapManifestUrl) { header("X-Client-Version", ArtifactVersion.value) }
            .execute()

        if (metaResponse.status.isSuccess()) {
          val manifestUrl = metaResponse.body<LeapModelManifestResponse>().manifest_url

          // Download and return the manifest schema
          val schemaResponse =
            client
              .prepareGet(manifestUrl) { header("X-Client-Version", ArtifactVersion.value) }
              .execute()

          if (schemaResponse.status.isSuccess()) {
            val body = schemaResponse.body<String>()
            if (body.isBlank()) {
              logger.w { "Resolved manifest body is empty: $manifestUrl" }
            } else {
              try {
                result = json.decodeFromString<Manifest>(body).copy(originalUrl = manifestUrl)
              } catch (e: Exception) {
                logger.w(e) { "Failed to decode resolved manifest from: $manifestUrl" }
              }
            }
          } else {
            logger.w { "Failed to resolve manifest: ${schemaResponse.status}" }
          }
        }
      }
    }

    return result
  }

  /**
   * Get the total size in bytes of a model.
   *
   * If the model is downloaded, this accumulates all file sizes on disk. If the model is not
   * downloaded, this resolves the manifest and makes HEAD requests to determine file sizes.
   *
   * @param modelName the model name
   * @param quantizationType the quantization type
   * @return the total size in bytes, or -1 if the size cannot be determined
   */
  override suspend fun getModelSize(modelName: String, quantizationType: String): Long {
    // Check if model exists on disk
    val resourceFolder = getResourceFolder(modelName, quantizationType)
    val resourcePath = Path(resourceFolder)
    val manifest = getCachedManifest(modelName, quantizationType)

    if (SystemFileSystem.exists(resourcePath) && manifest != null) {
      // Model is downloaded - sum up all file sizes
      return withContext(ioDispatcher) {
        var totalSize = 0L
        try {
          SystemFileSystem.list(resourcePath).forEach { filePath ->
            if (SystemFileSystem.metadataOrNull(filePath)?.isRegularFile == true) {
              totalSize += SystemFileSystem.metadataOrNull(filePath)?.size ?: 0L
            }
          }
        } catch (e: Exception) {
          logger.w(e) {
            "Failed to calculate size for downloaded model $modelName:$quantizationType"
          }
          return@withContext -1L
        }
        totalSize
      }
    }

    // Model is not downloaded - resolve manifest and get file sizes from HEAD requests
    return try {
      withContext(ioDispatcher) {
        // Resolve the manifest
        val resolvedManifest = resolve(modelName, quantizationType) ?: return@withContext -1L

        // Extract all file paths from manifest using extractResourceUrls
        val filePaths = extractResourceUrls(resolvedManifest)

        // Get the base URL for files
        val manifestBaseUrl =
          resolvedManifest.originalUrl
            ?: "${config.baseUrl ?: "https://leap.liquid.ai"}/models/$modelName/$quantizationType"

        // Make HEAD requests to get Content-Length for each file
        var totalSize = 0L
        useClient(factory = { defaultNoRedirectClient(json, config) }) { client ->
          filePaths.forEach { filePath ->
            val fileUrl =
              if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                filePath
              } else {
                "$manifestBaseUrl/$filePath"
              }

            try {
              client.prepareHead(fileUrl).execute { response ->
                if (response.status.isSuccess()) {
                  val contentLength =
                    response.contentLength() ?: response.headers["Content-Length"]?.toLongOrNull()
                  if (contentLength != null && contentLength > 0) {
                    totalSize += contentLength
                    logger.d { "File $filePath size: $contentLength bytes" }
                  }
                } else {
                  logger.w { "HEAD request failed for $fileUrl: ${response.status}" }
                }
              }
            } catch (e: Exception) {
              logger.w(e) { "Failed to get size for file: $fileUrl" }
            }
          }
        }

        if (totalSize > 0) totalSize else -1L
      }
    } catch (e: Exception) {
      logger.w(e) { "Failed to get model size for $modelName:$quantizationType" }
      -1L
    }
  }

  override suspend fun deleteModelResources(modelName: String, quantizationType: String) {
    val parentFolder = getResourceFolder(modelName = modelName, quantizationType = quantizationType)
    withContext(ioDispatcher) { SystemFileSystem.deleteRecursively(Path(parentFolder)) }
  }

  override suspend fun deleteModelFile(
    modelUrl: String,
    modelName: String,
    quantizationType: String,
  ) {
    val path =
      getPathFromUrl(
        url = modelUrl,
        manifestUrl = modelUrl,
        modelName = modelName,
        quantizationType = quantizationType,
      )
    withContext(ioDispatcher) { SystemFileSystem.delete(Path(path)) }
  }

  /**
   * Downloads a model from a manifest URL.
   *
   * Fetches the manifest JSON from the given URL, parses it into a Manifest, extracts resource URLs
   * and downloads them to the local cache.
   *
   * @param manifestUrl The URL of the manifest JSON file
   * @param progress Callback for download progress updates
   * @return The manifest with pathOnDisk set to the local manifest file
   */
  suspend fun downloadModelFromManifestUrl(
    manifestUrl: String,
    progress: (ProgressData) -> Unit = {},
  ): Manifest {
    // Fetch manifest from URL
    val manifestJson = useClient { client ->
      val response = client.prepareGet(manifestUrl).execute()
      if (!response.status.isSuccess()) {
        throw LeapModelLoadingException(
          "Failed to fetch manifest from $manifestUrl. Response: ${response.status}"
        )
      }
      response.body<String>()
    }

    // Parse manifest
    val manifest =
      try {
        json.decodeFromString<Manifest>(manifestJson).copy(originalUrl = manifestUrl)
      } catch (e: Exception) {
        logger.e(e) { "Failed to decode manifest from $manifestUrl" }
        throw LeapModelLoadingException("Failed to decode manifest from $manifestUrl", e)
      }

    // Derive modelName and quantizationType from manifest URL for cache directory naming
    // Use a hash of the URL to ensure uniqueness while keeping it filesystem-safe
    val urlHash = manifestUrl.hashCode().toUInt().toString(16)
    val modelName = "manifest-$urlHash"
    val quantizationType = "default"

    // Extract resources from manifest
    val resources = extractResourceUrls(manifest)

    // Download resources
    return resources.download(
      manifest = manifest,
      modelName = modelName,
      quantizationType = quantizationType,
      manifestUrl = manifestUrl,
      progress = progress,
    )
  }

  /**
   * Downloads and loads a model from a manifest URL.
   *
   * @param manifestUrl The URL of the manifest JSON file
   * @param options Optional model loading configuration
   * @param generationTimeParameters Optional generation-time parameters
   * @param progress Callback for download progress updates
   * @return A loaded ModelRunner instance
   */
  override suspend fun loadModelFromManifestUrl(
    manifestUrl: String,
    options: ModelLoadingOptions?,
    generationTimeParameters: GenerationTimeParameters?,
    progress: (ProgressData) -> Unit,
  ): ModelRunner {
    val manifest = downloadModelFromManifestUrl(manifestUrl, progress)

    // Construct loading options with chat template from manifest
    val loadingOptions =
      (options ?: ModelLoadingOptions()).copy(
        cpuThreads =
          generationTimeParameters?.numberOfDecodingThreads
            ?: manifest.generationTimeParameters?.numberOfDecodingThreads
            ?: CpuThreadAdvisor.getRecommendedThreadCount(),
        chatTemplate = manifest.loadTimeParameters.chatTemplate,
      )

    val manifestDir = manifest.pathOnDisk?.let { Path(it).parent }

    fun resolvePath(path: String): String {
      val p = Path(path)
      val resolvedPath =
        if (p.isAbsolute || manifestDir == null) {
          path
        } else {
          Path(manifestDir, path).toString()
        }
      return getAbsolutePath(resolvedPath)
    }

    return withContext(ioDispatcher) {
      when (val params = manifest.loadTimeParameters) {
        is ImageToTextLoadParams -> {
          LeapInferenceEngine.loadModel(
            modelPath = resolvePath(params.model),
            mmprojPath = resolvePath(params.multimodalProjector),
            audioDecoderPath = null,
            audioTokenizerPath = null,
            options = loadingOptions,
            generationTimeParameters = generationTimeParameters ?: manifest.generationTimeParameters,
          )
        }
        is Lfm2AudioV1LoadParams -> {
          LeapInferenceEngine.loadModel(
            modelPath = resolvePath(params.model),
            mmprojPath = resolvePath(params.multimodalProjector),
            audioDecoderPath = resolvePath(params.audioDecoder),
            audioTokenizerPath =
              params.audioTokenizer.takeIf { it.isNotBlank() }?.let { resolvePath(it) },
            options = loadingOptions.filterExtrasForAudio(),
            generationTimeParameters = generationTimeParameters ?: manifest.generationTimeParameters,
          )
        }
        is TextToTextLoadParams -> {
          LeapInferenceEngine.loadModel(
            modelPath = resolvePath(params.model),
            options = loadingOptions,
            generationTimeParameters = generationTimeParameters ?: manifest.generationTimeParameters,
          )
        }
      }
    }
  }

  /**
   * Deletes a model downloaded from a manifest URL.
   *
   * @param manifestUrl The URL of the manifest JSON file
   */
  override suspend fun deleteModelFromManifestUrl(manifestUrl: String) {
    val urlHash = manifestUrl.hashCode().toUInt().toString(16)
    val modelName = "manifest-$urlHash"
    val quantizationType = "default"
    deleteModelResources(modelName, quantizationType)
  }

  /**
   * Result of querying ETag and file size from a remote URL.
   *
   * @param etag The ETag header value (used for SHA256 validation), null if not available
   * @param size The Content-Length in bytes, null if not available
   */
  internal data class ETagAndSize(val etag: String?, val size: Long?)

  /**
   * Resolved resource information for a downloadable file.
   *
   * @param remoteUrl The remote URL to download from
   * @param localPath The local file path where the resource will be saved
   * @param expectedSize The expected file size in bytes (null if unknown)
   * @param expectedSha256 The expected SHA256 hash (null if unknown)
   * @param isCached Whether the file already exists locally with matching size
   */
  data class ResolvedResource(
    val remoteUrl: String,
    val localPath: String,
    val expectedSize: Long?,
    val expectedSha256: String?,
    val isCached: Boolean,
  )

  /**
   * Resolved manifest with download plan.
   *
   * @param manifest The manifest
   * @param modelName The model name used for caching
   * @param quantizationType The quantization type used for caching
   * @param resources List of resources to download
   */
  data class ResolvedManifest(
    val manifest: Manifest,
    val modelName: String,
    val quantizationType: String,
    val resources: List<ResolvedResource>,
  )

  /**
   * Resolve a download plan for a model by name and quantization.
   *
   * This resolves the manifest and determines which resources need to be downloaded, without
   * actually downloading them. Use this to separate manifest resolution from file downloading.
   *
   * @param modelName The model name
   * @param quantizationType The quantization method
   * @return ResolvedManifest with download plan
   * @throws LeapModelLoadingException if manifest resolution fails
   */
  override suspend fun resolveDownloadPlan(
    modelName: String,
    quantizationType: String,
  ): ResolvedManifest {
    // Resolve manifest
    val manifest =
      resolve(modelName, quantizationType)
        ?: throw LeapModelLoadingException(
          "Failed to resolve manifest for $modelName / $quantizationType"
        )

    // Get manifest URL
    val manifestUrl =
      if (manifest.originalUrl != null) {
        manifest.originalUrl
      } else {
        // Fetch from API
        val fetchedUrl = useClient { clientCache ->
          val leapManifestUrl =
            manifestUrl(modelName, quantizationType, config.baseUrl ?: "https://leap.liquid.ai")
          val metaResponse =
            clientCache
              .prepareGet(leapManifestUrl) { header("X-Client-Version", ArtifactVersion.value) }
              .execute()
          if (metaResponse.status.isSuccess()) {
            metaResponse.body<LeapModelManifestResponse>().manifest_url
          } else {
            throw LeapModelLoadingException(
              "Failed to fetch manifest URL for $modelName / $quantizationType"
            )
          }
        }
        fetchedUrl
      }

    // Resolve resources
    val resources = resolveResources(manifest, manifestUrl, modelName, quantizationType)

    return ResolvedManifest(
      manifest = manifest.copy(originalUrl = manifestUrl),
      modelName = modelName,
      quantizationType = quantizationType,
      resources = resources,
    )
  }

  /**
   * Resolve a download plan from a manifest URL.
   *
   * This fetches and parses the manifest, then determines which resources need to be downloaded,
   * without actually downloading them.
   *
   * @param manifestUrl The URL of the manifest JSON file
   * @return ResolvedManifest with download plan
   * @throws LeapModelLoadingException if manifest resolution fails
   */
  override suspend fun resolveDownloadPlanFromManifestUrl(manifestUrl: String): ResolvedManifest {
    // Fetch manifest from URL
    val manifestJson = useClient { client ->
      val response = client.prepareGet(manifestUrl).execute()
      if (!response.status.isSuccess()) {
        throw LeapModelLoadingException(
          "Failed to fetch manifest from $manifestUrl. Response: ${response.status}"
        )
      }
      response.body<String>()
    }

    // Parse manifest
    val manifest =
      try {
        json.decodeFromString<Manifest>(manifestJson).copy(originalUrl = manifestUrl)
      } catch (e: Exception) {
        logger.e(e) { "Failed to decode manifest from $manifestUrl" }
        throw LeapModelLoadingException("Failed to decode manifest from $manifestUrl", e)
      }

    // Derive modelName and quantizationType from manifest URL
    val urlHash = manifestUrl.hashCode().toUInt().toString(16)
    val modelName = "manifest-$urlHash"
    val quantizationType = "default"

    // Resolve resources
    val resources = resolveResources(manifest, manifestUrl, modelName, quantizationType)

    return ResolvedManifest(
      manifest = manifest,
      modelName = modelName,
      quantizationType = quantizationType,
      resources = resources,
    )
  }

  /**
   * Save a manifest to disk.
   *
   * This writes the manifest JSON file to the appropriate location in the cache directory. The
   * manifest's loadTimeParameters will be updated to use relative filenames instead of URLs.
   *
   * @param manifest The manifest to save
   * @param modelName The model name (used for cache directory naming)
   * @param quantizationType The quantization type (used for cache directory naming)
   * @return The updated manifest with loadTimeParameters containing filenames instead of URLs
   */
  override suspend fun saveManifestToDisk(
    manifest: Manifest,
    modelName: String,
    quantizationType: String,
  ): Manifest {
    return withContext(ioDispatcher) {
      val parentFolder =
        Path(getResourceFolder(modelName = modelName, quantizationType = quantizationType))

      if (!SystemFileSystem.exists(parentFolder)) {
        SystemFileSystem.createDirectories(parentFolder)
      }

      val manifestPath =
        Path(parentFolder.toString(), manifestFilename(modelName, quantizationType))
      val loadTimeParameters = parameters(manifest)
      val updatedManifest = manifest.copy(loadTimeParameters = loadTimeParameters)

      SystemFileSystem.sink(manifestPath).buffered().use {
        it.write(json.encodeToString(updatedManifest).encodeToByteArray())
      }

      logger.d { "Saved manifest to: $manifestPath" }
      updatedManifest
    }
  }

  /**
   * Extract resource URLs from manifest loadTimeParameters.
   *
   * @param manifest The manifest containing loadTimeParameters
   * @return List of resource URLs (may include blank strings that should be filtered)
   */
  private fun extractResourceUrls(manifest: Manifest): List<String> {
    return when (val params = manifest.loadTimeParameters) {
      is ImageToTextLoadParams ->
        listOf(params.model, params.multimodalProjector).filter { it.isNotBlank() }
      is Lfm2AudioV1LoadParams ->
        listOf(params.model, params.multimodalProjector, params.audioDecoder, params.audioTokenizer)
          .filter { it.isNotBlank() }
      is TextToTextLoadParams -> listOf(params.model).filter { it.isNotBlank() }
    }
  }

  /**
   * Resolve resources for a manifest into a download plan.
   *
   * @param manifest The manifest with resource URLs
   * @param manifestUrl The manifest URL (for resolving relative URLs)
   * @param modelName The model name (for cache directory)
   * @param quantizationType The quantization type (for cache directory)
   * @return List of resolved resources with download info
   */
  private suspend fun resolveResources(
    manifest: Manifest,
    manifestUrl: String,
    modelName: String,
    quantizationType: String,
  ): List<ResolvedResource> {
    val resourceUrls = extractResourceUrls(manifest)

    return coroutineScope {
      resourceUrls
        .map { url ->
          async {
            val resolvedUrl = url.fixRelativeUrl(manifestUrl)
            val localPath =
              getPathFromUrl(
                url = resolvedUrl,
                manifestUrl = manifestUrl,
                modelName = modelName,
                quantizationType = quantizationType,
              )

            // Check if cached
            val localFileSize = SystemFileSystem.metadataOrNull(Path(localPath))?.size
            val etagAndSize =
              if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
                getEtagAndSize(resolvedUrl)
              } else {
                ETagAndSize(null, null)
              }

            val isCached =
              localFileSize != null &&
                etagAndSize.size != null &&
                localFileSize == etagAndSize.size &&
                localFileSize > 0

            ResolvedResource(
              remoteUrl = resolvedUrl,
              localPath = localPath,
              expectedSize = etagAndSize.size,
              expectedSha256 = etagAndSize.etag,
              isCached = isCached,
            )
          }
        }
        .awaitAll()
    }
  }

  private suspend fun getEtagAndSize(url: String, redirectDepth: Int = 0): ETagAndSize {
    @Suppress("MagicNumber") val maxRedirects = 10

    if (redirectDepth >= maxRedirects) {
      logger.w { "Too many redirects (>= $maxRedirects) for $url, aborting" }
      return ETagAndSize(null, null)
    }

    return try {
      useClient(factory = { defaultNoRedirectClient(json, config) }) { client ->
        client.prepareHead(url).execute { response ->
          if (response.status.isSuccess() || response.status.value in 300..399) {
            val etag = response.headers["x-linked-etag"]?.removeQuotes()
            var contentLength = response.contentLength()
            if (contentLength == null || contentLength <= 0) {
              contentLength = response.headers["Content-Length"]?.toLongOrNull()
            }

            // If we got a redirect response, follow it to get the actual content length
            // but keep the original etag
            if (response.status.value in 300..399) {
              val location = response.headers["Location"]
              if (location != null) {
                // Resolve relative redirects against the original URL
                val resolvedLocation =
                  try {
                    val baseUrl = Url(url)
                    // Use URLBuilder to resolve relative URLs
                    val redirectUrl =
                      URLBuilder()
                        .apply {
                          takeFrom(baseUrl)
                          takeFrom(location)
                        }
                        .build()
                    redirectUrl.toString()
                  } catch (e: Exception) {
                    logger.w(e) { "Failed to resolve redirect location: $location against $url" }
                    location // Fall back to raw location
                  }

                logger.d {
                  "Following redirect from $url to $resolvedLocation (depth: $redirectDepth)"
                }
                // Get size from redirect target, but keep original etag
                val redirectResult = getEtagAndSize(resolvedLocation, redirectDepth + 1)
                return@execute ETagAndSize(etag, redirectResult.size)
              }
            }

            // Filter out small sizes that are likely redirect bodies or error pages
            @Suppress("MagicNumber")
            val verifiedContentLength =
              if (contentLength != null && contentLength > 1024) contentLength else null

            logger.d {
              "HEAD $url -> ETag: $etag, Content-Length: $verifiedContentLength (Raw: $contentLength, Status: ${response.status})"
            }
            ETagAndSize(etag, verifiedContentLength)
          } else {
            logger.w { "HEAD request failed for $url: ${response.status}" }
            ETagAndSize(null, null)
          }
        }
      }
    } catch (e: Exception) {
      logger.w(e) { "Failed to get ETag and size for $url" }
      ETagAndSize(null, null)
    }
  }

  private suspend fun getEtag(url: String): String? = getEtagAndSize(url).etag

  /**
   * Calculates the total download size for a list of resources by checking cached files and making
   * parallel HEAD requests for remote files.
   *
   * @param resources List of resource URLs to check
   * @param manifestUrl Optional manifest URL for resolving relative URLs
   * @param modelName Model name for cache path resolution
   * @param quantizationType Quantization slug for cache path resolution
   * @return Pair of (resource sizes map, total bytes)
   */
  private suspend fun calculateTotalSize(
    resources: List<String>,
    manifestUrl: String? = null,
    modelName: String,
    quantizationType: String,
  ): Pair<Map<String, Long>, Long> {
    val resourceSizes = mutableMapOf<String, Long>()
    var totalBytes = 0L

    // Parallel HEAD requests for remote files
    val remoteFileSizes =
      resources
        .mapNotNull { url ->
          if (url.isBlank()) return@mapNotNull null

          // Resolve URL and get cached path
          val resolvedUrl = manifestUrl?.let { url.fixRelativeUrl(it) } ?: url

          // Direct local-path short-circuit: if the resource is already an absolute path
          // (or file:// URL) that exists on disk, use its size directly and skip both the
          // HEAD request and the cache lookup. This matches loadSimpleModel's fast path.
          resolveLocalPath(resolvedUrl)?.let { localPath ->
            val size = SystemFileSystem.metadataOrNull(Path(localPath))?.size
            if (size != null && size > 0) {
              logger.d { "Resource $url is a local file, size: $size bytes" }
              return@mapNotNull url to size
            }
          }

          val cachedPath =
            if (manifestUrl != null) {
              // For manifest-based downloads, get path using getPathFromUrl
              getPathFromUrl(
                url = resolvedUrl,
                manifestUrl = manifestUrl,
                modelName = modelName,
                quantizationType = quantizationType,
              )
            } else {
              // For simple model downloads, use getCachedFilePath
              getCachedFilePath(
                modelUrl = url,
                modelName = modelName,
                quantizationType = quantizationType,
              )
            }

          // Check if file exists locally
          val localFileSize = cachedPath?.let { SystemFileSystem.metadataOrNull(Path(it))?.size }

          if (localFileSize != null && localFileSize > 0) {
            logger.d { "Resource $url cached locally, size: $localFileSize bytes" }
            url to localFileSize
          } else if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
            // File not cached, need to get size from HEAD request
            url to resolvedUrl
          } else {
            null
          }
        }
        .let { urlsToCheck ->
          // Separate cached (already have size) from remote (need HEAD request)
          val cached = urlsToCheck.filter { it.second is Long }
          val remote = urlsToCheck.filter { it.second is String }

          // Make HEAD requests in parallel for remote files
          val remoteSizes = coroutineScope {
            remote
              .map { (url, resolvedUrl) ->
                async {
                  val etagAndSize = getEtagAndSize(resolvedUrl as String)
                  if (etagAndSize.size != null && etagAndSize.size > 0) {
                    logger.d { "Resource $url size from HEAD: ${etagAndSize.size} bytes" }
                    url to etagAndSize.size
                  } else {
                    null
                  }
                }
              }
              .awaitAll()
              .filterNotNull()
          }

          // Combine cached and remote sizes
          cached.map { (url, size) -> url to (size as Long) } + remoteSizes
        }

    remoteFileSizes.forEach { (url, size) ->
      resourceSizes[url] = size
      totalBytes += size
    }
    logger.d { "Total download size: $totalBytes bytes (${totalBytes / 1024 / 1024} MB)" }

    if (totalBytes == 0L && resources.isNotEmpty()) {
      logger.w {
        "Unable to determine total download size. Progress reporting will be inaccurate. " +
          "This may occur if HEAD requests fail or servers don't provide Content-Length headers."
      }
    }

    return resourceSizes to totalBytes
  }

  /**
   * Loads a model from individual resource URLs without requiring a manifest file.
   *
   * Downloads and caches the model and any additional resource files (mmproj, audio decoder, audio
   * tokenizer) if they are not already present locally.
   *
   * @param model The source paths/URLs for the model and optional resource files
   * @param options Optional model loading configuration. Set `modelId` to control the folder name
   *   used for caching files.
   * @param generationTimeParameters Optional generation-time parameters
   * @param progress Callback for download progress updates
   * @return A loaded ModelRunner instance
   * @throws LeapModelLoadingException if the model file cannot be downloaded or found
   */
  override suspend fun loadSimpleModel(
    model: ModelSource,
    options: ModelLoadingOptions?,
    generationTimeParameters: GenerationTimeParameters?,
    isRetry: Boolean,
    progress: (ProgressData) -> Unit,
  ): ModelRunner {
    // Collect all resource URLs that need to be downloaded
    val resources =
      listOfNotNull(
        model.modelPath,
        model.mmprojPath,
        model.audioDecoderPath,
        model.audioTokenizerPath,
      )

    // Get total size of all resources (from cache or HEAD requests in parallel)
    val (resourceSizes, totalBytes) =
      calculateTotalSize(
        resources = resources,
        manifestUrl = null,
        modelName = model.modelName,
        quantizationType = model.quantizationId,
      )

    // Track cumulative bytes downloaded across all files
    // Protected by mutex to allow safe parallel downloads in the future
    var cumulativeBytes = 0L
    val progressMutex = Mutex()

    suspend fun cacheFile(resource: String): String? {
      logger.d("Caching: $resource")

      // Fast path: resource is already a local absolute path (or file:// URL) that exists
      // on disk. Skip cache lookup + download entirely and use the path verbatim. This is
      // the primary entry point for sideloaded GGUFs (adb push, app assets, bundled files).
      resolveLocalPath(resource)?.let { localPath ->
        val fileSize = SystemFileSystem.metadataOrNull(Path(localPath))?.size ?: 0L
        if (fileSize == 0L) {
          logger.w { "Local file $localPath has size 0 — skipping fast path" }
          return@let null
        }
        logger.d { "Using local file: $localPath ($fileSize bytes)" }
        progressMutex.withLock {
          cumulativeBytes += fileSize
          if (totalBytes == 0L) {
            // No totalBytes estimate (e.g. calculateTotalSize couldn't size it); report the
            // single file as its own 100%.
            progress(ProgressData(fileSize, fileSize))
          } else {
            progress(ProgressData(cumulativeBytes, totalBytes))
          }
        }
        return localPath
      }

      var cachedPath =
        getCachedFilePath(
          modelUrl = resource,
          modelName = model.modelName,
          quantizationType = model.quantizationId,
        )
      if (cachedPath == null) {
        // Capture current cumulative bytes before download to avoid locking on every progress
        // callback
        val cumulativeBytesSnapshot = progressMutex.withLock { cumulativeBytes }

        cachedPath =
          downloadUrl(
            url = resource,
            manifestUrl = resource,
            modelName = model.modelName,
            quantizationType = model.quantizationId,
            progress = { fileProgress ->
              if (totalBytes == 0L) {
                // No total size from HEAD requests, fall back to per-file progress
                progress(fileProgress)
              } else {
                // Report total progress using snapshot + current file progress
                val totalDownloaded = cumulativeBytesSnapshot + fileProgress.bytes
                progress(ProgressData(totalDownloaded, totalBytes))
              }
            },
          )
        // After successful download, get actual file size from disk (not estimated size)
        // This handles cases where the file was corrupted/partial and re-downloaded
        val actualFileSize = SystemFileSystem.metadataOrNull(Path(cachedPath))?.size
        if (actualFileSize != null && actualFileSize > 0) {
          progressMutex.withLock { cumulativeBytes += actualFileSize }
        } else {
          logger.w { "Could not get file size after download for $resource at $cachedPath" }
        }
      } else {
        // File already cached, get actual file size from disk
        val actualFileSize = SystemFileSystem.metadataOrNull(Path(cachedPath))?.size
        if (actualFileSize != null && actualFileSize > 0) {
          progressMutex.withLock {
            cumulativeBytes += actualFileSize
            if (totalBytes == 0L) {
              // No total size, report file size as both current and total (100%)
              progress(ProgressData(actualFileSize, actualFileSize))
            } else {
              progress(ProgressData(cumulativeBytes, totalBytes))
            }
          }
        } else {
          logger.w { "Could not get cached file size for $resource at $cachedPath" }
        }
      }
      return cachedPath
    }

    val localModelSource =
      ModelSource(
        modelPath =
          cacheFile(model.modelPath)
            ?: throw LeapModelLoadingException("Model file not found after download"),
        mmprojPath = model.mmprojPath?.let { cacheFile(it) },
        audioDecoderPath = model.audioDecoderPath?.let { cacheFile(it) },
        audioTokenizerPath = model.audioTokenizerPath?.let { cacheFile(it) },
        modelName = model.modelName,
        quantizationId = model.quantizationId,
      )

    return try {
      LeapInferenceEngine.loadModel(
        modelPath = localModelSource.modelPath,
        mmprojPath = localModelSource.mmprojPath,
        audioDecoderPath = localModelSource.audioDecoderPath,
        audioTokenizerPath = localModelSource.audioTokenizerPath,
        options = options,
        generationTimeParameters = generationTimeParameters,
      )
    } catch (e: Exception) {
      if (!isRetry) {
        logger.w(e) {
          "Model loading failed, possibly due to corruption. Deleting model resources and retrying."
        }
        deleteModelResources(modelName = model.modelName, quantizationType = model.quantizationId)
        loadSimpleModel(
          model = model,
          options = options,
          generationTimeParameters = generationTimeParameters,
          progress = progress,
          isRetry = true,
        )
      } else {
        logger.e(e) { "Model loading failed on retry attempt. Giving up." }
        throw e
      }
    }
  }

  override fun getCachedFilePath(
    modelUrl: String,
    modelName: String,
    quantizationType: String,
  ): String? {
    val parentFolder = getResourceFolder(modelName = modelName, quantizationType = quantizationType)
    val filename = modelUrl.extractFilename()
    // Trailing-slash and opaque URLs collapse to an empty segment list — without this guard
    // `Path(parentFolder, "")` resolves to the parent directory, so the existence check would
    // succeed on the cache folder and return its path as if it were a cached file.
    if (filename.isEmpty()) return null
    val resourcePath = Path(parentFolder, filename)
    return resourcePath.toString().takeIf { SystemFileSystem.exists(resourcePath) }
  }

  private fun getAbsolutePath(path: String): String {
    val p = Path(path)
    return if (p.isAbsolute) {
      path
    } else {
      Path(SystemFileSystem.resolve(Path(".")), path).toString()
    }
  }

  override suspend fun loadModel(
    modelName: String,
    quantizationType: String,
    options: ModelLoadingOptions?,
    generationTimeParameters: GenerationTimeParameters?,
    forceDownload: Boolean,
    isRetry: Boolean,
    progress: ((ProgressData) -> Unit)?,
  ): ModelRunner {
    val progressFn = progress ?: {}
    // Platform hook: returns a ModelRunner directly on wasmJs (fetch to MEMFS), null on others.
    platformLoadModel(
        modelName = modelName,
        quantizationType = quantizationType,
        modelLoadingOptions = options,
        generationTimeParameters = generationTimeParameters,
        forceDownload = forceDownload,
        progress = progressFn,
      )
      ?.let {
        return it
      }

    var loadedSchema = if (forceDownload) null else getCachedManifest(modelName, quantizationType)
    if (loadedSchema == null) {
      loadedSchema =
        withContext(ioDispatcher) {
          downloadModel(
            modelName = modelName,
            quantizationType = quantizationType,
            progress = progressFn,
          )
        }
    }
    loadedSchema.let { schema ->
      val loadingOptions =
        (options ?: ModelLoadingOptions()).copy(
          cpuThreads =
            generationTimeParameters?.numberOfDecodingThreads
              ?: schema.generationTimeParameters?.numberOfDecodingThreads
              ?: CpuThreadAdvisor.getRecommendedThreadCount(),
          chatTemplate = schema.loadTimeParameters.chatTemplate,
        )

      logger.d("Schema: ${json.encodeToString(schema)}")
      val manifestDir = schema.pathOnDisk?.let { Path(it).parent }

      fun resolvePath(path: String): String {
        val p = Path(path)
        val resolvedPath =
          if (p.isAbsolute || manifestDir == null) {
            path
          } else {
            Path(manifestDir, path).toString()
          }
        return getAbsolutePath(resolvedPath)
      }

      val allResourcesExist =
        when (val params = schema.loadTimeParameters) {
          is ImageToTextLoadParams ->
            SystemFileSystem.exists(Path(resolvePath(params.model))) &&
              SystemFileSystem.exists(Path(resolvePath(params.multimodalProjector)))

          is Lfm2AudioV1LoadParams ->
            SystemFileSystem.exists(Path(resolvePath(params.model))) &&
              SystemFileSystem.exists(Path(resolvePath(params.multimodalProjector))) &&
              SystemFileSystem.exists(Path(resolvePath(params.audioDecoder))) &&
              (params.audioTokenizer.isBlank() ||
                SystemFileSystem.exists(Path(resolvePath(params.audioTokenizer))))

          is TextToTextLoadParams -> SystemFileSystem.exists(Path(resolvePath(params.model)))
        }

      if (!allResourcesExist) {
        logger.i { "Some model resources are missing from disk. Re-downloading." }
        return loadModel(
          modelName = modelName,
          quantizationType = quantizationType,
          options = options,
          generationTimeParameters = generationTimeParameters,
          forceDownload = true,
          progress = progress,
          isRetry = isRetry,
        )
      }

      return withContext(ioDispatcher) {
        try {
          when (val params = schema.loadTimeParameters) {
            is ImageToTextLoadParams -> {
              LeapInferenceEngine.loadModel(
                modelPath = resolvePath(params.model),
                mmprojPath = resolvePath(params.multimodalProjector),
                audioDecoderPath = null,
                audioTokenizerPath = null,
                options = loadingOptions,
                generationTimeParameters =
                  generationTimeParameters ?: schema.generationTimeParameters,
              )
            }

            is Lfm2AudioV1LoadParams -> {
              LeapInferenceEngine.loadModel(
                modelPath = resolvePath(params.model),
                mmprojPath = resolvePath(params.multimodalProjector),
                audioDecoderPath = resolvePath(params.audioDecoder),
                audioTokenizerPath =
                  params.audioTokenizer.takeIf { it.isNotBlank() }?.let { resolvePath(it) },
                options = loadingOptions.filterExtrasForAudio(),
                generationTimeParameters =
                  generationTimeParameters ?: schema.generationTimeParameters,
              )
            }

            is TextToTextLoadParams -> {
              logger.d { "Loading from bundle: ${params.model}" }
              LeapInferenceEngine.loadModel(
                modelPath = resolvePath(params.model),
                options = loadingOptions,
                generationTimeParameters =
                  generationTimeParameters ?: schema.generationTimeParameters,
              )
            }
          }
        } catch (e: Exception) {
          if (!isRetry) {
            logger.w(e) {
              "Model loading failed, possibly due to corruption. Deleting model resources and retrying."
            }
            deleteModelResources(modelName = modelName, quantizationType = quantizationType)
            return@withContext loadModel(
              modelName = modelName,
              quantizationType = quantizationType,
              options = options,
              generationTimeParameters = generationTimeParameters,
              forceDownload = true,
              progress = progress,
              isRetry = true,
            )
          } else {
            logger.e(e) { "Model loading failed on retry attempt. Giving up." }
            throw e
          }
        }
      }
    }
  }

  suspend fun downloadModel(
    modelName: String,
    quantizationType: String,
    progress: (ProgressData) -> Unit = {},
  ): Manifest {
    // First resolve the real manifest URL from the meta endpoint, then fetch the schema
    val resolvedSchema =
      resolve(modelName, quantizationType)
        ?: error("Failed to resolve manifest for $modelName / $quantizationType")

    // If originalUrl is not set (e.g., from old cached manifest), fetch it from the API
    val manifest: Manifest =
      if (resolvedSchema.originalUrl == null) {
        logger.w { "Cached manifest missing originalUrl, fetching from API" }
        val fetchedUrl = useClient { clientCache ->
          val leapManifestUrl =
            manifestUrl(modelName, quantizationType, config.baseUrl ?: "https://leap.liquid.ai")
          val metaResponse =
            clientCache
              .prepareGet(leapManifestUrl) { header("X-Client-Version", ArtifactVersion.value) }
              .execute()
          if (metaResponse.status.isSuccess()) {
            metaResponse.body<LeapModelManifestResponse>().manifest_url
          } else {
            error("Failed to fetch manifest URL for $modelName / $quantizationType")
          }
        }
        resolvedSchema.copy(originalUrl = fetchedUrl)
      } else {
        resolvedSchema
      }
    val originalUrl = manifest.originalUrl ?: error("originalUrl should be set")

    val resources = extractResourceUrls(manifest)
    return resources.download(
      manifest = manifest,
      modelName = modelName,
      quantizationType = quantizationType,
      manifestUrl = originalUrl,
      progress = progress,
    )
  }

  @Suppress("LongMethod")
  private suspend fun List<String>.download(
    manifest: Manifest,
    modelName: String,
    quantizationType: String,
    manifestUrl: String,
    progress: (ProgressData) -> Unit,
  ): Manifest {
    // Schema will be found if the resources were already saved to disk
    val parentFolder =
      Path(getResourceFolder(modelName = modelName, quantizationType = quantizationType))
    if (!SystemFileSystem.exists(parentFolder)) {
      SystemFileSystem.createDirectories(parentFolder)
    }

    // Get total size of all resources (from cache or HEAD requests in parallel)
    val (resourceSizes, totalBytes) =
      calculateTotalSize(
        resources = this,
        manifestUrl = manifestUrl,
        modelName = modelName,
        quantizationType = quantizationType,
      )

    // Track cumulative bytes downloaded across all files
    // Protected by mutex to allow safe parallel downloads in the future
    var cumulativeBytes = 0L
    val progressMutex = Mutex()

    forEach { url ->
      try {
        val path = url.fixRelativeUrl(manifestUrl)
        val resourceFilename =
          getPathFromUrl(
            url = path,
            manifestUrl = manifestUrl,
            modelName = modelName,
            quantizationType = quantizationType,
          )

        // Check if file is already cached with matching size
        val isFileCached =
          SystemFileSystem.exists(Path(resourceFilename)) &&
            SystemFileSystem.metadataOrNull(Path(resourceFilename))?.size?.let { localSize ->
              resourceSizes[url]?.let { expectedSize -> localSize == expectedSize } ?: false
            } ?: false

        if (isFileCached) {
          // File already cached, get actual file size from disk
          val actualFileSize = SystemFileSystem.metadataOrNull(Path(resourceFilename))?.size
          if (actualFileSize != null && actualFileSize > 0) {
            progressMutex.withLock {
              cumulativeBytes += actualFileSize
              if (totalBytes == 0L) {
                // No total size, report file size as both current and total (100%)
                progress(ProgressData(actualFileSize, actualFileSize))
              } else {
                progress(ProgressData(cumulativeBytes, totalBytes))
              }
            }
          } else {
            logger.w { "Could not get cached file size for $url at $resourceFilename" }
          }
          logger.d { "Skipping download for cached file: $resourceFilename" }
        } else {
          // Capture current cumulative bytes before download to avoid locking on every progress
          // callback
          val cumulativeBytesSnapshot = progressMutex.withLock { cumulativeBytes }

          // File not cached or size mismatch, download it
          downloadUrl(
            url = url,
            manifestUrl = manifestUrl,
            modelName = modelName,
            quantizationType = quantizationType,
            progress = { fileProgress ->
              if (totalBytes == 0L) {
                // No total size from HEAD requests, fall back to per-file progress
                progress(fileProgress)
              } else {
                // Report total progress using snapshot + current file progress
                val totalDownloaded = cumulativeBytesSnapshot + fileProgress.bytes
                progress(ProgressData(totalDownloaded, totalBytes))
              }
            },
          )
          // After successful download, get actual file size from disk (not estimated size)
          // This handles cases where the file was corrupted/partial and re-downloaded
          val actualFileSize = SystemFileSystem.metadataOrNull(Path(resourceFilename))?.size
          if (actualFileSize != null && actualFileSize > 0) {
            progressMutex.withLock { cumulativeBytes += actualFileSize }
          } else {
            logger.w { "Could not get file size after download for $url at $resourceFilename" }
          }
        }
      } catch (e: Exception) {
        logger.e(e) { "Failed to download resource: $url" }
        throw e
      }
    }
    // save manifest file
    val manifestPath =
      Path(parentFolder.toString(), manifestFilename(modelName, quantizationType)).also {
        logger.d("Manifest path: $it")
      }
    val loadTimeParameters = parameters(manifest)
    val updatedManifest = manifest.copy(loadTimeParameters = loadTimeParameters)
    SystemFileSystem.sink(manifestPath).buffered().use {
      it.write(json.encodeToString(updatedManifest).encodeToByteArray())
    }
    return updatedManifest
  }

  private fun parameters(manifest: Manifest): LoadTimeParameters =
    when (val originalParams = manifest.loadTimeParameters) {
      is ImageToTextLoadParams ->
        originalParams.copy(
          model = originalParams.model.extractFilename(),
          multimodalProjector = originalParams.multimodalProjector.extractFilename(),
        )

      is Lfm2AudioV1LoadParams ->
        originalParams.copy(
          model = originalParams.model.extractFilename(),
          multimodalProjector = originalParams.multimodalProjector.extractFilename(),
          audioDecoder = originalParams.audioDecoder.extractFilename(),
          audioTokenizer = originalParams.audioTokenizer.extractFilename(),
        )

      is TextToTextLoadParams -> originalParams.copy(model = originalParams.model.extractFilename())
    }

  /**
   * Downloads a resource from the given URL and saves it to the appropriate directory. Ensures that
   * the parent directory is created if it does not exist and reuses existing files if they are
   * already downloaded. The progress of the download can be tracked using a callback.
   *
   * @param url The URL of the resource to download.
   * @param manifestUrl The URL of the manifest file used as a base reference for resolving relative
   *   URLs.
   * @param modelName A unique identifier for the model being downloaded, used for file
   *   organization.
   * @param quantizationType A unique identifier for the model's quantization variant, used for file
   *   organization.
   * @param progress A callback for tracking download progress, receiving updates about downloaded
   *   and total bytes.
   * @return The file path of the downloaded resource as a string.
   * @throws IOException if an error occurs during file writing or network communication.
   */
  private suspend fun downloadUrl(
    url: String,
    manifestUrl: String,
    modelName: String,
    quantizationType: String,
    progress: (ProgressData) -> Unit = {},
  ): String {
    val resourceFolder =
      getResourceFolder(modelName = modelName, quantizationType = quantizationType)
    val parentFolder = Path(resourceFolder)
    if (!SystemFileSystem.exists(parentFolder)) {
      SystemFileSystem.createDirectories(parentFolder)
    }
    if (url.isBlank()) {
      return ""
    }

    val path = url.fixRelativeUrl(manifestUrl)
    val filename = path.extractFilename()
    val resourceFilename =
      getPathFromUrl(
        url = path,
        manifestUrl = manifestUrl,
        modelName = modelName,
        quantizationType = quantizationType,
      )
    var cachedEtag: String? = null
    if (SystemFileSystem.exists(Path(resourceFilename))) {
      logger.d("Resource filename: $resourceFilename already exists")
      try {
        val etagAndSize = getEtagAndSize(path)
        val contentLength: Long? = etagAndSize.size
        cachedEtag = etagAndSize.etag

        if (contentLength != null) {
          val metadata = SystemFileSystem.metadataOrNull(Path(resourceFilename))
          if (metadata?.size == contentLength) {
            logger.d("Resource filename: $resourceFilename already exists and size matches")
            return SystemFileSystem.resolve(Path(resourceFilename)).toString()
          }
          logger.d("Resource filename: $resourceFilename exists but size mismatch. Re-downloading")
        } else {
          // If we can't get Content-Length, we can't be sure the file is complete.
          // In this case, we'll continue and try to download it.
          // Ktor's saveFile will re-download it.
          logger.w("Could not verify size for $resourceFilename (no Content-Length).")
        }
      } catch (e: Exception) {
        logger.w(e) { "Error verifying existing file $resourceFilename. Will attempt re-download." }
      }
    }

    if (!path.startsWith("http://") && !path.startsWith("https://")) {
      return SystemFileSystem.resolve(Path(resourceFilename)).toString()
    }
    val etag: String? = cachedEtag ?: getEtag(path)
    useClient(factory = { defaultClient(json, config) }) { client ->
      client.prepareGet(path).execute { response: HttpResponse ->
        if (!response.status.isSuccess()) {
          throw LeapModelLoadingException("Failed to download $path. Response: ${response.status}")
        }
        val contentLength = response.contentLength()
        response
          .saveFile(
            parentFolder = parentFolder,
            path = Path(filename),
            contentLength = contentLength,
            expectedSha256 = etag,
            progress = progress,
          )
          .let { saveFileInfo ->
            logger.d {
              "Downloaded $path to ${saveFileInfo.filepath}. Sha256: ${
                                saveFileInfo.sha256
                            } (Expected: $etag, Size: $contentLength, bytesRead: ${
                                saveFileInfo.bytesSaved
                            }"
            }
          }
      }
    }
    return SystemFileSystem.resolve(Path(resourceFilename)).toString()
  }

  data class SavedFileInfo(val sha256: String, val filepath: Path, val bytesSaved: Long)

  private suspend fun HttpResponse.saveFile(
    parentFolder: Path,
    path: Path,
    contentLength: Long?,
    expectedSha256: String?,
    progress: (ProgressData) -> Unit = {},
  ): SavedFileInfo {
    val source = Sha256Source(bodyAsChannel().asKotlinIoSource().buffered())
    val filepath = Path(parentFolder.toString(), path.toString())
    // Ensure any nested directories in the provided path are created too
    filepath.parent?.let { parent ->
      if (!SystemFileSystem.exists(parent)) {
        SystemFileSystem.createDirectories(parent)
      }
    }

    val bytesRead =
      source.use { src ->
        // Wrap the file sink with progress reporting, then buffer it and ensure the buffered sink
        // is closed
        val rawSink = SystemFileSystem.sink(path = filepath)
        val progressSink =
          rawSink.withProgressListener { bytes ->
            progress(ProgressData(bytes, contentLength ?: 0L))
          }
        progressSink.buffered().use { buffered -> buffered.transferFrom(source = src) }
      }
    // Only enforce size check when the server provided content length.
    if (contentLength != null && bytesRead != contentLength) {
      // save failed, delete file
      logger.d("Failed to save resource: $filepath. Sizes does not match")
      if (SystemFileSystem.exists(filepath)) {
        SystemFileSystem.delete(filepath)
      }
      throw LeapModelDownloadSizeMismatchException(
        expectedSize = contentLength,
        actualSize = bytesRead,
      )
    } else if (
      config.validateSha256 &&
        expectedSha256 != null &&
        !source.hashHex().equals(expectedSha256, true)
    ) {
      logger.d("Failed to save resource: $filepath. Sha256 hash does not match")
      if (SystemFileSystem.exists(filepath)) {
        SystemFileSystem.delete(filepath)
      }
      throw LeapModelDownloadSha256MismatchException(
        expectedSha256 = expectedSha256.lowercase(),
        actualSha256 = source.hashHex(),
      )
    }
    return SavedFileInfo(sha256 = source.hashHex(), filepath = filepath, bytesSaved = bytesRead)
  }
}

fun manifestUrl(
  modelName: String,
  quantizationType: String,
  baseUrl: String = "https://leap.liquid.ai",
): String =
  "$baseUrl/api/edge-sdk/model-manifest?model_name=$modelName&quantization_method=$quantizationType&platform=Android"

fun manifestFilename(modelName: String, quantizationType: String): String =
  "$modelName-$quantizationType.json"

@Serializable data class LeapModelManifestResponse(val manifest_url: String)

expect fun buildHttpClient(
  json: Json,
  installCache: Boolean = false,
  followRedirects: Boolean = true,
  expectSuccess: Boolean = true,
  disableSslValidation: Boolean = false,
  connectTimeoutMillis: Long = 30_000,
  socketTimeoutMillis: Long = 60_000,
  requestTimeoutMillis: Long = 120_000,
): HttpClient

fun defaultClient(json: Json, config: LeapDownloaderConfig) =
  buildHttpClient(
    json,
    disableSslValidation = config.disableSslValidation,
    connectTimeoutMillis = config.connectTimeoutMillis,
    socketTimeoutMillis = config.socketTimeoutMillis,
    requestTimeoutMillis = config.requestTimeoutMillis,
  )

fun defaultClientCache(json: Json, config: LeapDownloaderConfig) =
  buildHttpClient(
    json,
    installCache = true,
    disableSslValidation = config.disableSslValidation,
    connectTimeoutMillis = config.connectTimeoutMillis,
    socketTimeoutMillis = config.socketTimeoutMillis,
    requestTimeoutMillis = config.requestTimeoutMillis,
  )

fun defaultNoRedirectClient(json: Json, config: LeapDownloaderConfig) =
  buildHttpClient(
    json,
    followRedirects = false,
    expectSuccess = false,
    disableSslValidation = config.disableSslValidation,
    connectTimeoutMillis = config.connectTimeoutMillis,
    socketTimeoutMillis = config.socketTimeoutMillis,
    requestTimeoutMillis = config.requestTimeoutMillis,
  )

internal expect val ioDispatcher: CoroutineContext

internal expect val hasLocalFileSystem: Boolean

internal expect fun ByteReadChannel.asKotlinIoSource(): Source

private fun ModelLoadingOptions?.filterExtrasForAudio(): ModelLoadingOptions? {
  val raw = this?.extras ?: return this
  val filtered =
    try {
      val parsed = Json.parseToJsonElement(raw)
      if (parsed !is JsonObject) return copy(extras = null)
      val supported = setOf("number_of_decoder_threads")
      val obj = buildJsonObject { parsed.forEach { (k, v) -> if (k in supported) put(k, v) } }
      obj.takeIf { it.isNotEmpty() }?.toString()
    } catch (_: Exception) {
      null
    }
  return copy(extras = filtered)
}

/**
 * Resolve a [ModelSource] field to a local filesystem path if it points at a file that already
 * exists on disk. Returns `null` otherwise (HTTP/HTTPS URLs, relative paths, nonexistent files).
 *
 * Used by [LeapDownloader.loadSimpleModel] to short-circuit the cache + download path for resources
 * that are already pre-staged locally — e.g. GGUFs pushed to a device via `adb push` or bundled in
 * app assets. HTTP(S) resources continue through the existing download path.
 *
 * Explicit `http://` / `https://` exclusion guards against platform-dependent behavior of
 * [Path.isAbsolute] on URI-like strings — iOS native and wasm `kotlinx.io` may behave differently
 * than JVM on such inputs, and we don't want a platform quirk to make `downloadUrl` accidentally
 * skipped for a remote URL.
 *
 * Handles `file:///absolute/path` (RFC 8089 canonical form) by stripping the `file://` prefix,
 * leaving `/absolute/path`. Percent-encoded characters (e.g. `%20` for space) are decoded after
 * prefix removal. Note: `file://relative/path` strips to `relative/path` which is NOT absolute —
 * the `isAbsolute` check rejects it safely. Authority-based URIs other than empty or `localhost`
 * (e.g. `file://remote-host/path`) return null rather than being silently misinterpreted as
 * relative paths.
 */
internal fun resolveLocalPath(resource: String): String? {
  if (resource.startsWith("http://") || resource.startsWith("https://")) return null
  val path =
    if (resource.startsWith("file://")) {
      val stripped =
        when {
          // RFC 8089 §3: an empty authority and "localhost" are equivalent.
          resource.startsWith("file:///") -> resource.removePrefix("file://")
          resource.startsWith("file://localhost/") -> resource.removePrefix("file://localhost")
          // Any other authority (e.g. file://hostname/path) is a remote/UNC reference; refuse it
          // rather than mangling it into a relative-looking path.
          else -> return null
        }
      try {
        stripped.decodeURLPart()
      } catch (_: IllegalArgumentException) {
        stripped
      }
    } else {
      resource
    }
  val p = Path(path)
  return if (p.isAbsolute && SystemFileSystem.exists(p)) path else null
}
