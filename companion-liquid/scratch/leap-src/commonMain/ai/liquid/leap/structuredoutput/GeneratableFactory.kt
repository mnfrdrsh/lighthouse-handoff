package ai.liquid.leap.structuredoutput

import ai.liquid.leap.LeapJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

object GeneratableFactory {
  /**
   * Create generatable object instances from JSON object.
   *
   * @param jsonObject JsonObject to be deserialized into a data class object.
   * @param serializer [KSerializer] of the data class for creating the JSONSchema.
   */
  @Throws(LeapGeneratableDeserializationException::class)
  fun <T : Any> createFromJsonObject(jsonObject: JsonObject, serializer: KSerializer<T>): T {
    try {
      return LeapJson.decodeFromJsonElement(serializer, jsonObject)
    } catch (e: Exception) {
      throw LeapGeneratableDeserializationException(
        "Failed to deserialize JSON object into ${serializer.descriptor.serialName}",
        e,
      )
    }
  }

  @Throws(LeapGeneratableDeserializationException::class)
  inline fun <reified T : Any> createFromJsonObject(jsonObject: JsonObject): T {
    val serializer =
      try {
        serializer<T>()
      } catch (t: Throwable) {
        throw LeapGeneratableDeserializationException(
          "Type must be @Serializable to be created by GeneratableFactory",
          t,
        )
      }
    return createFromJsonObject(jsonObject, serializer)
  }
}
