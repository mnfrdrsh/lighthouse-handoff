package ai.liquid.leap.io

import kotlin.math.min
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlinx.io.write
import org.kotlincrypto.hash.sha2.SHA256

/**
 * A RawSource wrapper that computes a SHA-256 hash of the bytes read from an underlying source.
 *
 * This source reads from [delegate], updates the digest with the read bytes and then forwards the
 * same data to the caller-provided [Buffer].
 */
class Sha256Source(private val delegate: RawSource) : RawSource {
  private val digest: SHA256 = SHA256()
  private var closed: Boolean = false
  private var finalizedHash: ByteArray? = null

  /** Total number of bytes read from this source. */
  var bytesRead: Long = 0
    private set

  private val scratch: Buffer = Buffer()

  override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
    check(!closed) { "Sha256Source is closed" }
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }

    // Read from the delegate into a temporary buffer so we can hash the bytes first.
    val toRead = byteCount
    val read = delegate.readAtMostTo(scratch, toRead)
    if (read <= 0) return read

    var remaining = read
    while (remaining > 0) {
      val step = min(remaining, 8192L).toInt()
      val chunk = scratch.readByteArray(step)
      digest.update(chunk)
      sink.write(chunk)
      remaining -= chunk.size
      bytesRead += chunk.size
    }
    return read
  }

  override fun close() {
    if (closed) return
    finalizedHash = digest.digest()
    closed = true
    delegate.close()
  }

  /**
   * Returns the current SHA-256 hash as a lowercase hex string. If closed, returns the finalized
   * hash; otherwise attempts to compute a snapshot using a copy of the digest.
   */
  fun hashHex(): String = hashBytes()?.toHexString() ?: ""

  /**
   * Returns the current SHA-256 hash bytes. If closed, returns the finalized value; otherwise tries
   * to compute a snapshot using a copy of the digest. If not supported, returns null.
   */
  fun hashBytes(): ByteArray? {
    finalizedHash?.let {
      return it.copyOf()
    }
    return try {
      val copy = digest.copy()
      copy.digest()
    } catch (_: Exception) {
      null
    }
  }
}
