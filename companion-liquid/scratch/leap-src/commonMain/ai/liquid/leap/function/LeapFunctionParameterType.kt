package ai.liquid.leap.function

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Represent a type that can be used for the parameters of Leap functions. All types declared must
 * be allowed in JSON Schema.
 *
 * @param description an optional, human-readable and LLM-readable description of this type
 */
sealed class LeapFunctionParameterType(typeDescription: kotlin.String? = null) {
  @OptIn(ExperimentalObjCName::class)
  @ObjCName("typeDescription")
  var description: kotlin.String? = typeDescription
    private set

  /** Create a JsonObject representation of this type, which will be consumed by LLM */
  open fun toJsonObject(): JsonObject = buildJsonObject {
    description?.let { put("description", it) }
  }

  /** Attach a description onto this type. The existing description will be overwritten. */
  internal fun attachDescription(description: kotlin.String): LeapFunctionParameterType {
    this.description = description
    return this
  }

  /** LeapStr literal type. */
  class LeapStr(val enumValues: List<kotlin.String>? = null, description: kotlin.String? = null) :
    LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "string")
      if (enumValues != null) {
        put("enum", buildJsonArray { enumValues.forEach { add(it) } })
      }
    }
  }

  /** LeapNum literal type. */
  class LeapNum(val enumValues: List<kotlin.Number>? = null, description: kotlin.String? = null) :
    LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "number")
      if (enumValues != null) {
        put("enum", buildJsonArray { enumValues.forEach { add(it) } })
      }
    }
  }

  /** LeapInt literal type. */
  class LeapInt(val enumValues: List<Int>? = null, description: kotlin.String? = null) :
    LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "integer")
      if (enumValues != null) {
        put("enum", buildJsonArray { enumValues.forEach { add(it) } })
      }
    }
  }

  /** LeapBool literal type. */
  class LeapBool(description: kotlin.String? = null) : LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "boolean")
    }
  }

  /** LeapArr type. `itemType` indicates the type of its element. */
  class LeapArr(val itemType: LeapFunctionParameterType, description: kotlin.String? = null) :
    LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "array")
      put("items", itemType.toJsonObject())
    }
  }

  /**
   * LeapObj type.
   *
   * @param properties map of property names to their types
   * @param required list of required property names
   * @param description optional description text for LLM to understand the effect of this parameter
   */
  class LeapObj(
    val properties: Map<kotlin.String, LeapFunctionParameterType>,
    val required: List<kotlin.String> = listOf(),
    description: kotlin.String? = null,
  ) : LeapFunctionParameterType(description) {
    override fun toJsonObject(): JsonObject = buildJsonObject {
      description?.let { put("description", it) }
      put("type", "object")
      put(
        "properties",
        buildJsonObject { properties.forEach { (key, value) -> put(key, value.toJsonObject()) } },
      )
      put("required", buildJsonArray { required.forEach { add(it) } })
    }
  }

  /** LeapNull type: only null value is accepted. */
  class LeapNull : LeapFunctionParameterType() {
    override fun toJsonObject(): JsonObject = buildJsonObject { put("type", "null") }
  }
}
