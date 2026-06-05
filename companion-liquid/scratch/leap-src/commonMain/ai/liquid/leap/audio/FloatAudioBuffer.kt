package ai.liquid.leap.audio

import ai.liquid.leap.message.ChatMessageContent
import kotlinx.io.*

/**
 * A buffer for accumulating floating-point audio samples and converting them to WAV format.
 *
 * This class generates 32-bit float PCM WAV files (IEEE 754 format). The samples are stored as raw
 * floating-point values and converted to WAV format with proper headers when needed.
 *
 * **Breaking Change (v0.9.7)**: The constructor now accepts an optional `channelCount` parameter
 * for multi-channel audio support. Existing code using `FloatAudioBuffer(sampleRate)` will continue
 * to work (defaults to mono), but any subclasses that override the constructor may need updates.
 *
 * **Memory Considerations**: This buffer can accumulate unlimited samples, which may lead to
 * out-of-memory errors for very long audio recordings. Consider periodically processing and
 * clearing the buffer for streaming scenarios, or use the buffer size limits if needed.
 *
 * **Duration Precision**: The duration calculation uses floating-point arithmetic, which may
 * accumulate small rounding errors for extremely long audio files (multiple hours). This is not a
 * practical concern for typical use cases.
 *
 * @property sampleRate The audio sample rate in Hz (e.g., 16000, 44100)
 * @property channelCount The number of audio channels (1 = mono, 2 = stereo)
 */
class FloatAudioBuffer(val sampleRate: Int, val channelCount: Int = 1) {
  private val sampleChunks = mutableListOf<FloatArray>()
  private var totalSize = 0

  init {
    require(sampleRate > 0) { "Sample rate must be positive, got: $sampleRate" }
    require(channelCount > 0) { "Channel count must be positive, got: $channelCount" }
  }

  /** Returns true if the buffer contains no samples */
  val isEmpty: Boolean
    get() = totalSize == 0

  /** Returns the total number of samples in the buffer (across all channels) */
  val count: Int
    get() = totalSize

  /**
   * Returns the duration of the audio in seconds.
   *
   * The duration is calculated as: (number of frames) / sample rate, where the number of frames is
   * (total samples) / channel count.
   *
   * Note: Uses floating-point arithmetic which may have minor precision issues for very long audio
   * (multiple hours).
   */
  val duration: Double
    get() {
      if (totalSize == 0) return 0.0
      val frames = totalSize.toDouble() / channelCount.toDouble()
      return frames / sampleRate.toDouble()
    }

  /**
   * Add samples to the buffer.
   *
   * @param chunk Array of float samples to append (must be aligned to channel count)
   * @throws IllegalArgumentException if chunk size is not divisible by channel count or buffer
   *   would exceed maximum size
   */
  fun add(chunk: FloatArray) {
    require(chunk.size % channelCount == 0) {
      "Chunk size (${chunk.size}) must be divisible by channel count ($channelCount)"
    }

    // Check that adding this chunk won't exceed maximum safe buffer size
    // Leave headroom for WAV header (44 bytes) and prevent integer overflow
    val bytePerSample = 4 // 32-bit float
    val newTotalSize = totalSize + chunk.size
    val newDataByteCount = newTotalSize.toLong() * bytePerSample
    val maxSafeDataSize = (Int.MAX_VALUE - 100).toLong() // Leave headroom for header

    require(newDataByteCount <= maxSafeDataSize) {
      "Adding chunk would exceed maximum buffer size: " +
        "current=$totalSize samples, adding=${chunk.size} samples, " +
        "would be ${newDataByteCount} bytes (max: $maxSafeDataSize bytes)"
    }

    this.sampleChunks.add(chunk)
    totalSize += chunk.size
  }

  /**
   * Append samples to the buffer. Alias for [add] that matches iOS SDK naming.
   *
   * @param samples Array of float samples to append
   */
  fun append(samples: FloatArray) {
    add(samples)
  }

  /** Remove all samples from the buffer */
  fun clear() {
    sampleChunks.clear()
    totalSize = 0
  }

  /**
   * Convert the buffer to WAV-encoded bytes (32-bit float PCM format, IEEE 754).
   *
   * The generated WAV file uses:
   * - Format: IEEE 754 32-bit float PCM
   * - Bits per sample: 32
   * - Byte order: Little-endian
   * - Header size: 44 bytes
   *
   * @return ByteArray containing a complete WAV file with header
   * @throws IllegalArgumentException if buffer is too large to fit in a WAV file
   */
  @OptIn(UnsafeIoApi::class)
  fun createWavBytes(): ByteArray {
    val bytePerSample = 4 // 32-bit float = 4 bytes
    val dataByteCount = totalSize * bytePerSample

    // WAV header fields
    val blockAlign = channelCount * bytePerSample // Bytes per frame (all channels)

    // Validate blockAlign fits in 16-bit unsigned integer (WAV format requirement)
    require(channelCount <= 16383) {
      "Channel count too large for WAV format: $channelCount channels × $bytePerSample bytes = " +
        "${channelCount * bytePerSample} bytes per frame (max: 65535 bytes)"
    }
    require(blockAlign <= 65535) {
      "Block align exceeds WAV format limit (65535 bytes): $blockAlign bytes " +
        "(channelCount=$channelCount × bytePerSample=$bytePerSample)"
    }

    val byteRate = sampleRate * blockAlign // Bytes per second

    // Validate total size fits in WAV format (RIFF chunk size is 32-bit)
    val totalWavSize = 36L + dataByteCount.toLong()
    require(totalWavSize <= Int.MAX_VALUE) {
      "Buffer too large for WAV format: $totalWavSize bytes (max: ${Int.MAX_VALUE} bytes). " +
        "Total samples: $totalSize, data size: $dataByteCount bytes"
    }

    val buffer = Buffer()

    // WAV header is 44 bytes total
    buffer.writeString("RIFF")
    buffer.writeIntLe(36 + dataByteCount)
    buffer.writeString("WAVEfmt ")
    buffer.writeIntLe(16)
    buffer.writeShortLe(3) // Float sample format (IEEE 754)
    buffer.writeShortLe(channelCount.toShort())
    buffer.writeIntLe(sampleRate)
    buffer.writeIntLe(byteRate)
    buffer.writeShortLe(blockAlign.toShort())
    buffer.writeShortLe(32) // bits per sample
    buffer.writeString("data")
    buffer.writeIntLe(dataByteCount)

    for (chunk in this.sampleChunks) {
      for (v in chunk) {
        buffer.writeFloatLe(v)
      }
    }
    return buffer.readByteArray()
  }

  /**
   * Concatenate all chunks into a single [FloatArray].
   *
   * @return A new FloatArray containing all samples in order
   */
  fun toFloatArray(): FloatArray {
    if (totalSize == 0) return FloatArray(0)
    val result = FloatArray(totalSize)
    var offset = 0
    for (chunk in sampleChunks) {
      chunk.copyInto(result, offset)
      offset += chunk.size
    }
    return result
  }

  /**
   * Create a [ChatMessageContent.Audio] from the buffer's samples.
   *
   * This converts the floating-point samples to WAV format (32-bit float PCM) and wraps them in a
   * ChatMessageContent suitable for use in conversations.
   *
   * @return ChatMessageContent.Audio containing WAV-encoded data
   */
  fun makeAudioContent(): ChatMessageContent.Audio {
    return ChatMessageContent.Audio(createWavBytes())
  }

  /**
   * Create a [ChatMessageContent.AudioPcmF32] from the buffer's raw samples.
   *
   * This preserves the raw float32 samples without WAV encoding, suitable for roundtripping
   * engine-generated audio through conversation history without hash loss.
   *
   * @return ChatMessageContent.AudioPcmF32 containing raw PCM samples and sample rate
   */
  fun makePcmF32AudioContent(): ChatMessageContent.AudioPcmF32 {
    return ChatMessageContent.AudioPcmF32(toFloatArray(), sampleRate)
  }
}
