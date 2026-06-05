package ai.liquid.leap.manifest

import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * A RawSource wrapper that reports progress to a listener.
 *
 * @param delegate The underlying RawSource from which the data is actually read.
 * @param progressListener A lambda function that receives the total number of bytes read so far.
 */
class ProgressReportingSource(
  private val delegate: RawSource,
  private val progressListener: (Long) -> Unit,
) : RawSource {
  private var bytesRead: Long = 0

  override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
    val read = delegate.readAtMostTo(sink, byteCount)
    if (read > 0) {
      bytesRead += read
      progressListener(bytesRead)
    }
    return read
  }

  override fun close() {
    delegate.close()
  }
}

/** Extension function to easily wrap any existing RawSource with progress reporting. */
fun RawSource.withProgressListener(progressListener: (Long) -> Unit): RawSource =
  ProgressReportingSource(this, progressListener)
