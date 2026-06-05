package ai.liquid.leap.io

import kotlin.math.min
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.readByteArray
import org.kotlincrypto.hash.sha2.SHA256

/**
 * A RawSink implementation that consumes incoming bytes to compute a SHA-256 hash.
 *
 * This sink is a passthrough for hashing purposes only: it does not forward data to any underlying
 * sink and simply discards written bytes after updating the digest.
 */
class Sha256Sink : RawSink {
  private val digest: SHA256 = SHA256()
  private var closed: Boolean = false
  private var finalizedHash: ByteArray? = null

  /** Total number of bytes written to this sink. */
  var bytesWritten: Long = 0
    private set

  override fun write(source: Buffer, byteCount: Long) {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "Sha256Sink is closed" }

    var remaining = byteCount
    // Read from the buffer in chunks to avoid large intermediate arrays.
    while (remaining > 0) {
      val toRead = min(remaining, 8192L).toInt()
      val chunk = source.readByteArray(toRead)
      digest.update(chunk)
      remaining -= chunk.size
      bytesWritten += chunk.size
    }
  }

  override fun flush() {
    // No-op: nothing to forward.
  }

  override fun close() {
    if (closed) return
    // Finalize the digest once on close for stable results afterwards.
    finalizedHash = digest.digest()
    closed = true
  }

  /**
   * Returns the current SHA-256 hash of the bytes written so far as a lowercase hex string. If the
   * sink has been closed, returns the finalized hash; otherwise attempts to compute a snapshot
   * using a clone of the digest. If cloning isn't supported, the hash will be computed only after
   * [close] is called.
   */
  fun hashHex(): String = hashBytes()?.toHexString() ?: ""

  /**
   * Returns the current SHA-256 hash bytes of the data written so far. If the sink is closed,
   * returns the finalized hash. If not closed, tries to clone the digest to produce a snapshot
   * without finalizing; if cloning isn't supported, returns null.
   */
  fun hashBytes(): ByteArray? {
    finalizedHash?.let {
      return it.copyOf()
    }
    // Try to snapshot without finalizing using a copy of the hasher. If not supported, return null.
    return try {
      val copy = digest.copy()
      copy.digest()
    } catch (_: Exception) {
      // Fallback: cannot compute until close() finalizes the digest
      null
    }
  }
}
