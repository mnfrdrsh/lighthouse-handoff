package ai.liquid.leap.manifest

import kotlinx.io.Buffer
import kotlinx.io.RawSink

/**
 * A RawSink wrapper that reports progress to a listener.
 *
 * @param delegate The underlying RawSink to which the data is actually written.
 * @param progressListener A lambda function that receives the total number of bytes written so far.
 */
class ProgressReportingSink(
  private val delegate: RawSink,
  private val progressListener: (Long) -> Unit,
) : RawSink {
  private var bytesWritten: Long = 0

  override fun write(source: Buffer, byteCount: Long) {
    // Write the data to the underlying sink
    delegate.write(source, byteCount)
    // Update the counter and notify the listener
    bytesWritten += byteCount
    progressListener(bytesWritten)
  }

  override fun flush() {
    delegate.flush()
  }

  override fun close() {
    delegate.close()
  }
}

/** Extension function to easily wrap any existing Sink with progress reporting. */
fun RawSink.withProgressListener(progressListener: (Long) -> Unit): RawSink =
  ProgressReportingSink(this, progressListener)
