package ai.liquid.leap.manifest

import io.ktor.http.Url
import io.ktor.utils.io.CancellationException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

fun String.removeQuotes(): String = this.replace("\"", "")

fun String.extractFilename(): String {
  val segments = Url(this).segments
  return segments.lastOrNull { it.isNotBlank() } ?: ""
}

fun String.extractBaseUrl(): String = substringBeforeLast("/") + "/"

// Fix relative URLs that start with "../" by resolving them against the base URL of the source URL.
fun String.fixRelativeUrl(sourceUrl: String): String {
  val extractedBaseUrl = sourceUrl.extractBaseUrl()
  val filename = extractFilename()
  val downloadUrl =
    when {
      startsWith("../") -> {
        extractedBaseUrl.removeSuffix("/").substringBeforeLast("/") + "/" + filename
      }

      else -> {
        this
      }
    }
  return downloadUrl
}

/**
 * Extract a compact model name from a Hugging Face URL.
 *
 * Example: https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/leap/Q4_K_M.json ->
 * LFM2-1.2B-GGUF-leap-Q4_K_M
 *
 * Convention: If the URL contains ".../<repo>/resolve/<branch>/<subpaths...>/<filename>", the model
 * name is: <repo>-<subpaths-joined-with-dashes>-<filenameWithoutExtension>. If there are no
 * subpaths after the branch, it falls back to: <repo>-<filenameWithoutExtension>.
 */
fun String.extractHfFileName(keepExtension: Boolean = false): String {
  return try {
    val url = Url(this)
    val segments = url.segments.map { it.trim('/') }.filter { it.isNotBlank() }
    // Expecting: <org>/<repo>/resolve/<branch>/.../<filename>
    val repo = segments.getOrNull(1)
    val filename = segments.lastOrNull()
    if (repo.isNullOrBlank() || filename.isNullOrBlank()) return ""

    val nameNoExt = filename.substringBeforeLast('.')
    if (!keepExtension && nameNoExt.isBlank()) return ""

    val resolveIndex = segments.indexOf("resolve")
    val branchIndex = if (resolveIndex >= 0) resolveIndex + 1 else -1
    val hasBranch = branchIndex in segments.indices

    val filePart = if (keepExtension) filename else nameNoExt

    if (resolveIndex >= 0 && hasBranch) {
      // subpaths are between branch and filename (exclusive)
      val subpathStart = branchIndex + 1
      val subpathEnd = segments.size - 1 // exclude filename
      val subpaths =
        if (subpathStart < subpathEnd) segments.subList(subpathStart, subpathEnd) else emptyList()
      if (subpaths.isNotEmpty()) {
        // Join subpaths and append filename (with or without extension per flag)
        return "$repo-${subpaths.joinToString(separator = "-")}-$filePart"
      }
    }

    // Fallback to previous rule
    "$repo-$filePart"
  } catch (_: Throwable) {
    ""
  }
}

fun String.getResourceFolder(manifestUrl: String): String {
  val resourceFilename = this.fixRelativeUrl(manifestUrl)
  return resourceFilename.extractHfFileName()
}

/**
 * Same as [runCatching], but does not catch [CancellationException], throwing it instead, making it
 * safe to use with coroutines.
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
  @Suppress("TooGenericExceptionCaught")
  try {
    Result.success(block())
  } catch (ce: CancellationException) {
    throw ce
  } catch (e: Throwable) {
    Result.failure(e)
  }

/**
 * Delete the given path, recursively if needed
 *
 * @throws kotlinx.io.files.FileNotFoundException - when [path] does not exist and [mustExist] is
 *   `true`
 * @throws kotlinx.io.IOException if there was an underlying error preventing listing the [path]'s
 *   children if it was a directory
 */
fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = false) {
  if (!exists(path)) {
    if (mustExist) {
      throw kotlinx.io.files.FileNotFoundException("File does not exist: ${path.toString()}")
    }
    return
  }
  val isDirectory = metadataOrNull(path)?.isDirectory ?: false
  if (isDirectory) {
    list(path).forEach { child -> deleteRecursively(child, mustExist) }
  }
  delete(path, mustExist)
}
