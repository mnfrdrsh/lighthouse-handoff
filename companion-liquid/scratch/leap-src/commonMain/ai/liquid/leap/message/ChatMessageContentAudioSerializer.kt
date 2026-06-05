package ai.liquid.leap.message

import ai.liquid.leap.serialization.ByteArrayBase64Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Minimal serializer for ChatMessageContent.Audio that injects the discriminator `type` while
 * keeping standard sealed polymorphism (no base custom serializer).
 */
object ChatMessageContentAudioSerializer : KSerializer<ChatMessageContent.Audio> {
  // Serial name must match the discriminator value for proper sealed subtype resolution
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("input_audio")

  override fun serialize(encoder: Encoder, value: ChatMessageContent.Audio) {
    val jsonEncoder =
      encoder as? JsonEncoder
        ?: throw SerializationException("ChatMessageContent.Audio can only be serialized to JSON")

    // Encode the nested input_audio object using the existing nested @Serializable model
    val nested = buildJsonObject {
      // data as base64 via ByteArrayBase64Serializer
      val dataBase64 =
        kotlinx.serialization.json.Json.encodeToJsonElement(
          ByteArrayBase64Serializer,
          value.inputAudio.data,
        )
      put("data", dataBase64)
      put("format", value.inputAudio.format)
    }

    val obj = buildJsonObject {
      put("type", "input_audio")
      put("input_audio", nested)
    }
    jsonEncoder.encodeJsonElement(obj)
  }

  override fun deserialize(decoder: Decoder): ChatMessageContent.Audio {
    val jsonDecoder =
      decoder as? JsonDecoder
        ?: throw SerializationException(
          "ChatMessageContent.Audio can only be deserialized from JSON"
        )
    val elem = jsonDecoder.decodeJsonElement()
    val obj =
      elem as? JsonObject
        ?: throw SerializationException("Expected JsonObject for ChatMessageContent.Audio")

    val audioObj =
      obj["input_audio"]?.jsonObject
        ?: throw SerializationException("Missing 'input_audio' object for Audio content")
    val dataBase64 =
      audioObj["data"]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing 'data' for Audio content")
    val format = audioObj["format"]?.jsonPrimitive?.content ?: "wav"

    val dataElem = kotlinx.serialization.json.JsonPrimitive(dataBase64)
    val data = jsonDecoder.json.decodeFromJsonElement(ByteArrayBase64Serializer, dataElem)
    return ChatMessageContent.Audio(
      ChatMessageContent.Audio.InputAudio(data = data, format = format)
    )
  }
}
