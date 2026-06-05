package ai.liquid.leap.message

import ai.liquid.leap.LeapSerializationException
import ai.liquid.leap.function.LeapFunctionCall
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Kotlinx serializer for [ChatMessage] that preserves the existing OpenAI-compatible JSON shape
 * without requiring an intermediate DTO.
 */
object ChatMessageJsonSerializer : KSerializer<ChatMessage> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatMessage")

  override fun serialize(encoder: Encoder, value: ChatMessage) {
    val jsonEncoder =
      encoder as? JsonEncoder
        ?: throw SerializationException("ChatMessage can only be serialized to JSON")
    val contentJson =
      jsonEncoder.json.encodeToJsonElement(
        ListSerializer(ChatMessageContent.serializer()),
        value.content,
      )

    val toolCallsJson: JsonArray? =
      value.functionCalls?.let { calls ->
        if (calls.isEmpty()) null
        else {
          buildJsonArray {
            calls.forEachIndexed { idx, fc ->
              add(
                buildJsonObject {
                  put("index", idx)
                  put("id", "call_${'$'}idx")
                  put("type", "function")
                  val funcJson =
                    jsonEncoder.json.encodeToJsonElement(LeapFunctionCall.serializer(), fc)
                  put("function", funcJson)
                }
              )
            }
          }
        }
      }

    val obj = buildJsonObject {
      put("role", value.role.type)
      put("content", contentJson)
      value.reasoningContent?.let { put("reasoning_content", it) }
      toolCallsJson?.let { put("tool_calls", it) }
    }

    jsonEncoder.encodeJsonElement(obj)
  }

  override fun deserialize(decoder: Decoder): ChatMessage {
    val jsonDecoder =
      decoder as? JsonDecoder
        ?: throw SerializationException("ChatMessage can only be deserialized from JSON")

    val rawElem = jsonDecoder.decodeJsonElement()
    val obj =
      rawElem as? JsonObject ?: throw SerializationException("Expected JsonObject for ChatMessage")

    // Normalize legacy shapes before decoding into DTO:
    // - Allow `content` to be either a string or an array of content objects. If it's a string,
    //   wrap it into a single text content element matching the OpenAI shape used elsewhere.
    val normalizedObj = normalizeChatMessageJson(obj)

    return try {
      // role
      val roleStr =
        normalizedObj["role"]?.jsonPrimitive?.content
          ?: throw LeapSerializationException("Missing role")
      val role = ChatMessage.Role.fromTypeString(roleStr)

      // content
      val contentElem =
        normalizedObj["content"] ?: throw LeapSerializationException("Missing content")
      val content: List<ChatMessageContent> =
        jsonDecoder.json.decodeFromJsonElement(
          ListSerializer(ChatMessageContent.serializer()),
          contentElem,
        )

      // reasoning_content
      val rcRaw = (normalizedObj["reasoning_content"] as? JsonPrimitive)?.content
      val reasoningContent: String? = if (rcRaw.isNullOrEmpty()) null else rcRaw

      // tool_calls
      val functionCalls: List<LeapFunctionCall>? =
        normalizedObj["tool_calls"]?.let { tcElem ->
          val arr = tcElem.jsonArray
          val calls =
            arr.map { item ->
              val functionElem =
                item.jsonObject["function"]
                  ?: throw LeapSerializationException("tool_calls.function is missing")
              jsonDecoder.json.decodeFromJsonElement(LeapFunctionCall.serializer(), functionElem)
            }
          calls.takeIf { it.isNotEmpty() }
        }

      ChatMessage(
        role = role,
        content = content,
        reasoningContent = reasoningContent,
        functionCalls = functionCalls,
      )
    } catch (e: LeapSerializationException) {
      throw e
    } catch (e: Exception) {
      // Map any remaining issues to LeapSerializationException
      throw LeapSerializationException("Failed to deserialize ChatMessage", e)
    }
  }

  private fun normalizeChatMessageJson(obj: JsonObject): JsonObject {
    val contentElement = obj["content"]
    val normalizedContent =
      when (contentElement) {
        is JsonPrimitive -> {
          if (contentElement.isString) {
            val text = contentElement.content
            // Build array: [{"type":"text","text": text}]
            buildJsonArray {
              add(
                buildJsonObject {
                  put("type", "text")
                  put("text", text)
                }
              )
            }
          } else {
            // Non-string primitive content is invalid in our contract; keep as-is to let decoding
            // fail
            contentElement
          }
        }
        is JsonArray -> contentElement
        is JsonObject -> contentElement
        null -> null
      }

    if (normalizedContent === contentElement) return obj // no change

    // Rebuild object with normalized content
    return buildJsonObject {
      obj.forEach { (k, v) ->
        if (k == "content") {
          when (normalizedContent) {
            null -> put(k, JsonNull)
            is JsonArray -> put(k, normalizedContent)
            is JsonObject -> put(k, normalizedContent)
            is JsonPrimitive -> put(k, normalizedContent)
          }
        } else {
          put(k, v)
        }
      }
    }
  }
}
