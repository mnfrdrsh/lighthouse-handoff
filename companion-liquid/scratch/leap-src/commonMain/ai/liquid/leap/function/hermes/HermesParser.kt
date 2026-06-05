package ai.liquid.leap.function.hermes

import ai.liquid.leap.LeapJson
import ai.liquid.leap.function.LeapFunctionCall
import kotlin.collections.set
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

internal object HermesParser {
  @Throws(SerializationException::class)
  fun parse(functionCallCode: String): LeapFunctionCall {
    val jsonCall = LeapJson.parseToJsonElement(functionCallCode).jsonObject
    val name =
      jsonCall["name"]?.jsonPrimitive?.contentOrNull
        ?: throw SerializationException("Missing or invalid 'name' field")
    val arguments =
      jsonCall["arguments"]?.jsonObject
        ?: throw SerializationException("Missing or invalid 'arguments' field")
    val argumentsMap = parseJsonObject(arguments)
    return LeapFunctionCall(name, argumentsMap)
  }

  private fun parseJsonObject(obj: JsonObject): Map<String, Any> {
    val ret = hashMapOf<String, Any>()
    for ((key, element) in obj) {
      when (element) {
        is JsonObject -> {
          ret[key] = parseJsonObject(element)
        }
        is JsonArray -> {
          ret[key] = parseJsonArray(element)
        }
        is JsonPrimitive -> {
          val value =
            when {
              element.isString -> element.content
              element.booleanOrNull != null -> element.boolean
              element.longOrNull != null -> {
                val longValue = element.long
                // Prefer Int when the value fits (to align with expectations in tests and
                // other parsers)
                if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) longValue.toInt() else longValue
              }
              element.doubleOrNull != null -> element.double
              else -> element.content
            }
          ret[key] = value
        }
      }
    }
    return ret
  }

  private fun parseJsonArray(array: JsonArray): List<Any> {
    val ret = mutableListOf<Any>()
    for (element in array) {
      when (element) {
        is JsonObject -> {
          ret.add(parseJsonObject(element))
        }
        is JsonArray -> {
          ret.add(parseJsonArray(element))
        }
        is JsonPrimitive -> {
          val value =
            when {
              element.isString -> element.content
              element.booleanOrNull != null -> element.boolean
              element.longOrNull != null -> {
                val longValue = element.long
                // Prefer Int when the value fits
                if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) longValue.toInt() else longValue
              }
              element.doubleOrNull != null -> element.double
              else -> element.content
            }
          ret.add(value)
        }
        is JsonNull -> {
          // Skip null values
        }
      }
    }
    return ret
  }
}
