package ai.liquid.leap.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Custom serializer for Map<LeapStr, JsonElement>.
 *
 * This is used for flexible JSON arguments in function calls where the structure is not known at
 * compile time. The map can contain any JSON-compatible values.
 */
object JsonElementMapSerializer : KSerializer<Map<String, JsonElement>> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JsonElementMap")

  override fun serialize(encoder: Encoder, value: Map<String, JsonElement>) {
    val jsonEncoder =
      encoder as? JsonEncoder
        ?: throw SerializationException("JsonElementMap can only be serialized to JSON")

    val obj = buildJsonObject { value.forEach { (k, v) -> put(k, v) } }
    jsonEncoder.encodeJsonElement(obj)
  }

  override fun deserialize(decoder: Decoder): Map<String, JsonElement> {
    val jsonDecoder =
      decoder as? JsonDecoder
        ?: throw SerializationException("JsonElementMap can only be deserialized from JSON")

    val obj = jsonDecoder.decodeJsonElement().jsonObject
    return obj.toMap()
  }
}
