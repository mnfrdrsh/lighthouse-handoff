package ai.liquid.leap.inferenceengine

import ai.liquid.leap.LeapGenerationFunctionCallParsingException
import ai.liquid.leap.ModelRunner.GenerationCallback
import ai.liquid.leap.function.LeapFunctionCall
import ai.liquid.leap.function.LeapFunctionCallParser
import ai.liquid.leap.message.MessageResponse

/**
 * Processes tokens from the inference engine and routes them to appropriate handlers.
 *
 * This class encapsulates the logic for:
 * - Filtering special tokens (tokens marked with isSpecial flag)
 * - Routing reasoning content (<think> / </think> tags)
 * - Routing function call tokens (start/end tokens from parser)
 * - Emitting regular text as MessageResponse.Chunk
 * - Supporting inline thinking mode
 */
class TokenProcessor(
  private val callback: GenerationCallback,
  private val functionCallParser: LeapFunctionCallParser?,
  private val inlineThinking: Boolean = false,
) {
  val buffer = StringBuilder()
  val reasoningBuffer = StringBuilder()
  val functionCallBuffer = mutableListOf<LeapFunctionCall>()

  private var isReasoning = false
  private var isInFunctionCall = false

  /**
   * Process a single token from the inference engine.
   *
   * @param chunk The token string (null tokens are silently ignored)
   * @param isSpecial Whether the token is marked as special by the inference engine
   */
  fun processToken(chunk: String?, isSpecial: Boolean) {
    if (chunk == null) {
      return
    }

    // Filter out special tokens (stop tokens, control tokens, etc.)
    // unless they are explicitly handled below
    if (
      isSpecial &&
        chunk != REASONING_START_TAG &&
        chunk != REASONING_END_TAG &&
        chunk != functionCallParser?.toolCallStartToken &&
        chunk != functionCallParser?.toolCallEndToken
    ) {
      return
    }

    when (chunk) {
      REASONING_START_TAG -> {
        isReasoning = true
        if (inlineThinking) {
          // Emit the tag as a regular chunk
          buffer.append(chunk)
          callback.onResponse(MessageResponse.Chunk(chunk))
        }
      }

      REASONING_END_TAG -> {
        isReasoning = false
        if (inlineThinking) {
          // Emit the tag as a regular chunk
          buffer.append(chunk)
          callback.onResponse(MessageResponse.Chunk(chunk))
        }
      }

      functionCallParser?.toolCallStartToken -> {
        isInFunctionCall = true
      }

      functionCallParser?.toolCallEndToken -> {
        isInFunctionCall = false
        try {
          val functionCallResult = functionCallParser.parse()
          functionCallBuffer.addAll(functionCallResult)
          callback.onResponse(MessageResponse.FunctionCalls(functionCallResult))
        } catch (e: Exception) {
          callback.onError(LeapGenerationFunctionCallParsingException("", e))
        }
      }

      else -> {
        if (isReasoning) {
          reasoningBuffer.append(chunk)
          if (inlineThinking) {
            // Emit thinking content as regular chunks with tags intact
            buffer.append(chunk)
            callback.onResponse(MessageResponse.Chunk(chunk))
          } else {
            // Default behavior: emit as separate ReasoningChunk
            callback.onResponse(MessageResponse.ReasoningChunk(chunk))
          }
        } else if (isInFunctionCall) {
          functionCallParser?.append(chunk)
        } else {
          buffer.append(chunk)
          callback.onResponse(MessageResponse.Chunk(chunk))
        }
      }
    }
  }

  companion object {
    const val REASONING_START_TAG = "<think>"
    const val REASONING_END_TAG = "</think>"
  }
}
