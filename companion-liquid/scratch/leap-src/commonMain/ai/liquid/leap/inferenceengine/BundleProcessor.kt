package ai.liquid.leap.inferenceengine

import co.touchlab.kermit.Logger

/**
 * Internal utility for extracting bundle configuration from zip files.
 *
 * Uses the `ie_zip` C library (shipped alongside `libinference_engine` in the vendor c-lib artifact
 * bundle) via cinterop for all Kotlin/Native platforms; Android/JVM fall back to the platform's
 * built-in java.util.zip APIs.
 */
internal object BundleProcessor {
  private val logger = Logger.withTag("BundleProcessor")

  /**
   * Loads config.yaml from a bundle zip file.
   *
   * @param bundlePath Path to the bundle zip file
   * @return Contents of config.yaml, or null if the file doesn't exist or can't be read
   */
  fun loadBundleConfigString(bundlePath: String): String? {
    return try {
      logger.d { "Loading bundle config from: $bundlePath" }
      val content = extractZipEntry(bundlePath, "config.yaml")
      if (content != null) {
        logger.d { "Successfully loaded config.yaml from bundle" }
      } else {
        logger.w { "config.yaml not found in bundle: $bundlePath" }
      }
      content
    } catch (e: Exception) {
      logger.w(e) { "Failed to read bundle: $bundlePath" }
      null
    }
  }
}

/**
 * Platform-specific zip extraction via Rust FFI.
 *
 * @param zipPath Path to the zip file
 * @param entryName Name of the entry to extract (e.g., "config.yaml")
 * @return Contents of the entry, or null if not found or error occurs
 */
internal expect fun extractZipEntry(zipPath: String, entryName: String): String?
