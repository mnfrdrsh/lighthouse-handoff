package ai.liquid.leap.inferenceengine

import co.touchlab.kermit.Logger
import java.util.zip.ZipFile

/** JVM implementation of BundleProcessor using java.util.zip. */
internal actual fun extractZipEntry(zipPath: String, entryName: String): String? {
  return try {
    ZipFile(zipPath).use { zf ->
      zf.getEntry(entryName)?.let { entry ->
        zf.getInputStream(entry).use { it.bufferedReader(Charsets.UTF_8).readText() }
      }
    }
  } catch (e: Exception) {
    Logger.withTag("BundleProcessor").e(e) {
      "Error extracting zip entry: $entryName from $zipPath"
    }
    null
  }
}
