package ai.liquid.leap.function.hermes

import ai.liquid.leap.LeapJson
import ai.liquid.leap.function.LeapFunctionCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object HermesDumper {
  fun dump(functionCall: LeapFunctionCall): String {
    val obj = buildJsonObject {
      put("name", functionCall.name)
      put("arguments", dumpObject(functionCall.arguments))
    }
    return LeapJson.encodeToString(JsonObject.serializer(), obj)
  }

  private fun dumpObject(obj: Map<String, Any?>): JsonObject = buildJsonObject {
    for ((key, value) in obj.entries) {
      when (value) {
        is Map<*, *> -> {
          @Suppress("UNCHECKED_CAST") put(key, dumpObject(value as Map<String, Any?>))
        }
        is List<*> -> {
          put(key, dumpArray(value))
        }
        null -> {
          put(key, JsonPrimitive(null as String?))
        }
        is String -> {
          put(key, value)
        }
        is Number -> {
          put(key, value)
        }
        is Boolean -> {
          put(key, value)
        }
        else -> {
          put(key, value.toString())
        }
      }
    }
  }

  private fun dumpArray(array: List<Any?>): JsonArray = buildJsonArray {
    for (value in array) {
      when (value) {
        is Map<*, *> -> {
          @Suppress("UNCHECKED_CAST") add(dumpObject(value as Map<String, Any?>))
        }
        is List<*> -> {
          add(dumpArray(value))
        }
        null -> {
          add(JsonPrimitive(null as String?))
        }
        is String -> {
          add(JsonPrimitive(value))
        }
        is Number -> {
          add(JsonPrimitive(value))
        }
        is Boolean -> {
          add(JsonPrimitive(value))
        }
        else -> {
          add(JsonPrimitive(value.toString()))
        }
      }
    }
  }
}
