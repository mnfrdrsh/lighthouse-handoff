package ai.liquid.leap.message

import ai.liquid.leap.serialization.ByteArrayBase64Serializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Interface for all message content classes */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ChatMessageContent {

  /** Pure text content. */
  @Serializable @SerialName("text") data class Text(val text: String) : ChatMessageContent()

  /**
   * Image content. The wire shape must be: { "type": "image_url", "image_url": { "url":
   * "data:image/jpeg;base64,<...>" } }
   */
  @SerialName("image_url")
  @Serializable(with = ChatMessageContentImageSerializer::class)
  data class Image(@SerialName("image_url") val imageUrl: ImageUrl) : ChatMessageContent() {

    @OptIn(ExperimentalEncodingApi::class)
    val jpegByteArray: ByteArray
      get() {
        val url = imageUrl.url
        val base64 =
          if (url.startsWith(JPEG_BASE64_DATA_URL_PREFIX))
            url.removePrefix(JPEG_BASE64_DATA_URL_PREFIX)
          else url
        return Base64.decode(base64)
      }

    @OptIn(ExperimentalEncodingApi::class)
    constructor(
      jpegByteArray: ByteArray
    ) : this(imageUrl = ImageUrl(url = JPEG_BASE64_DATA_URL_PREFIX + Base64.encode(jpegByteArray)))

    @Serializable data class ImageUrl(val url: String)
  }

  /**
   * Audio content. The wire shape must be: { "type": "input_audio", "input_audio": { "data":
   * "<base64>", "format": "wav" } }
   */
  @SerialName("input_audio")
  @Serializable(with = ChatMessageContentAudioSerializer::class)
  data class Audio(@SerialName("input_audio") val inputAudio: InputAudio) : ChatMessageContent() {

    val data: ByteArray
      get() = inputAudio.data

    constructor(data: ByteArray) : this(inputAudio = InputAudio(data = data))

    @Serializable
    data class InputAudio(
      @Serializable(with = ByteArrayBase64Serializer::class) val data: ByteArray,
      val format: String = "wav",
    ) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as InputAudio

        if (!data.contentEquals(other.data)) return false
        if (format != other.format) return false

        return true
      }

      override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + format.hashCode()
        return result
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as Audio
      return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Audio(wavByteArraySize=${data.size})"
  }

  /**
   * Raw PCM float32 mono audio content. Used for engine-generated audio that needs to roundtrip
   * through conversation history without hash loss. User-provided audio (microphone recordings,
   * file uploads) should use [Audio] instead.
   *
   * @property samples Raw float32 PCM samples
   * @property sampleRate Sample rate in Hz (e.g. 24000)
   */
  @Serializable
  @SerialName("audio_pcm_f32")
  data class AudioPcmF32(val samples: FloatArray, val sampleRate: Int) : ChatMessageContent() {

    /** Convert to WAV byte array for playback or file storage. */
    fun toWavBytes(): ByteArray {
      val buffer = ai.liquid.leap.audio.FloatAudioBuffer(sampleRate)
      buffer.add(samples)
      return buffer.createWavBytes()
    }

    /** Convert to the WAV-based [Audio] content type. */
    fun toAudio(): Audio = Audio(toWavBytes())

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as AudioPcmF32
      if (!samples.contentEquals(other.samples)) return false
      if (sampleRate != other.sampleRate) return false
      return true
    }

    override fun hashCode(): Int {
      var result = samples.contentHashCode()
      result = 31 * result + sampleRate
      return result
    }

    override fun toString(): String =
      "AudioPcmF32(samplesSize=${samples.size}, sampleRate=$sampleRate)"
  }

  companion object {
    const val JPEG_BASE64_DATA_URL_PREFIX = "data:image/jpeg;base64,"

    /**
     * Factory method for creating Text content.
     *
     * This provides a convenient factory method that works across all platforms.
     *
     * Usage:
     * ```kotlin
     * val content = ChatMessageContent.text("Hello")
     * ```
     */
    fun text(text: String): Text = Text(text)
  }
}
