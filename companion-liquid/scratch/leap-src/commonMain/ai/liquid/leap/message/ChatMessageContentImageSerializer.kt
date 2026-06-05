package ai.liquid.leap.message

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
 * Minimal serializer for ChatMessageContent.Image that injects the discriminator `type` while
 * keeping standard sealed polymorphism (no base custom serializer).
 */
object ChatMessageContentImageSerializer : KSerializer<ChatMessageContent.Image> {
  // Serial name must match the discriminator value for proper sealed subtype resolution
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("image_url")

  override fun serialize(encoder: Encoder, value: ChatMessageContent.Image) {
    val jsonEncoder =
      encoder as? JsonEncoder
        ?: throw SerializationException("ChatMessageContent.Image can only be serialized to JSON")

    val obj = buildJsonObject {
      put("type", "image_url")
      put("image_url", buildJsonObject { put("url", value.imageUrl.url) })
    }
    jsonEncoder.encodeJsonElement(obj)
  }

  override fun deserialize(decoder: Decoder): ChatMessageContent.Image {
    val jsonDecoder =
      decoder as? JsonDecoder
        ?: throw SerializationException(
          "ChatMessageContent.Image can only be deserialized from JSON"
        )
    val elem = jsonDecoder.decodeJsonElement()
    val obj =
      elem as? JsonObject
        ?: throw SerializationException("Expected JsonObject for ChatMessageContent.Image")

    val imageObj =
      obj["image_url"]?.jsonObject
        ?: throw SerializationException("Missing 'image_url' object for Image content")
    val url =
      imageObj["url"]?.jsonPrimitive?.content
        ?: throw SerializationException("Missing 'url' for Image content")
    return ChatMessageContent.Image(ChatMessageContent.Image.ImageUrl(url))
  }
}
