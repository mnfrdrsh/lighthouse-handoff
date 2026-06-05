package ai.liquid.leap.manifest

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner

/**
 * Opt-in marker for Leap SDK internal seams that are visible in the public Maven artifact (because
 * they live in `commonMain`) but are not part of the supported API. Implementing or calling such a
 * type from outside the SDK requires `@OptIn(InternalLeapApi::class)` and is liable to break
 * between minor releases.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message = "Internal Leap SDK seam — not part of the supported API.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalLeapApi

/**
 * Substitution seam for the parts of [LeapDownloader] that downstream classes (today: iOS
 * `LeapModelDownloader`) depend on. Lets test code pass a fake without subclassing the concrete
 * downloader.
 *
 * The signature set mirrors [LeapDownloader] exactly so wrapping it is `: DownloaderEngine` with
 * `override` keywords — no behavior shift. Default parameter values are kept aligned with the
 * concrete class so call sites that pass through the interface look identical.
 *
 * Note: this is intentionally a narrow slice, not a complete mirror of every public method on
 * [LeapDownloader]. Methods not used by external consumers (e.g. `getPathFromUrl`,
 * `downloadModelFromManifestUrl` raw) stay on the concrete class. The interface grows when a real
 * caller needs more.
 *
 * Marked [InternalLeapApi]: external code should not implement or call this interface directly — it
 * exists to support in-SDK testing seams and may change between minor releases.
 */
@InternalLeapApi
interface DownloaderEngine {
  // MARK: - Path / cache lookup

  fun getResourceFolder(modelName: String, quantizationType: String): String

  fun getCachedFilePath(modelUrl: String, modelName: String, quantizationType: String): String?

  suspend fun getCachedManifest(modelName: String, quantizationType: String): Manifest?

  // MARK: - Manifest resolution

  suspend fun resolve(modelName: String, quantizationType: String): Manifest?

  suspend fun resolveDownloadPlan(
    modelName: String,
    quantizationType: String,
  ): LeapDownloader.ResolvedManifest

  suspend fun resolveDownloadPlanFromManifestUrl(
    manifestUrl: String
  ): LeapDownloader.ResolvedManifest

  suspend fun saveManifestToDisk(
    manifest: Manifest,
    modelName: String,
    quantizationType: String,
  ): Manifest

  // MARK: - Size queries

  suspend fun getModelSize(modelName: String, quantizationType: String): Long

  // MARK: - Deletion

  suspend fun deleteModelResources(modelName: String, quantizationType: String)

  suspend fun deleteModelFile(modelUrl: String, modelName: String, quantizationType: String)

  suspend fun deleteModelFromManifestUrl(manifestUrl: String)

  // MARK: - Loading

  suspend fun loadModel(
    modelName: String,
    quantizationType: String,
    options: ModelLoadingOptions? = null,
    generationTimeParameters: GenerationTimeParameters? = null,
    forceDownload: Boolean = false,
    isRetry: Boolean = false,
    progress: ((ProgressData) -> Unit)? = null,
  ): ModelRunner

  suspend fun loadModelFromManifestUrl(
    manifestUrl: String,
    options: ModelLoadingOptions? = null,
    generationTimeParameters: GenerationTimeParameters? = null,
    progress: (ProgressData) -> Unit = {},
  ): ModelRunner

  suspend fun loadSimpleModel(
    model: ModelSource,
    options: ModelLoadingOptions? = null,
    generationTimeParameters: GenerationTimeParameters? = null,
    isRetry: Boolean = false,
    progress: (ProgressData) -> Unit = {},
  ): ModelRunner
}
