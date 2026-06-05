package ai.liquid.leap

import ai.liquid.leap.function.LFMFunctionCallParser
import ai.liquid.leap.function.LeapFunctionCallParser
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.structuredoutput.JSONSchemaGenerator

/**
 * Options to control the generation. For any fields setting as null, the default parameter values
 * from the model bundle will be used.
 */
data class GenerationOptions(
  /**
   * Sampling temperature parameter.
   *
   * Higher values will make the output more random, while lower values will make it more focused
   * and deterministic.
   */
  var temperature: Float? = null,
  /**
   * Nucleus sampling parameter.
   *
   * In nucleus sampling, the model only considers the results of the tokens with top_p probability
   * mass.
   */
  var topP: Float? = null,
  /** Minimal possibility for a token to be considered in generation. */
  var minP: Float? = null,
  /**
   * Repetition penalty parameter.
   *
   * A positive value will decrease the model's likelihood to repeat the same line verbatim.
   */
  var repetitionPenalty: Float? = null,
  /**
   * Top-K sampling parameter.
   *
   * Only consider the K most likely next tokens during sampling. Lower values make output more
   * focused by restricting the candidate pool, while higher values allow more diversity. A value of
   * 0 or null disables top-K filtering.
   */
  var topK: Int? = null,
  /**
   * Random number generator seed for reproducible generation.
   *
   * When set, the model will produce deterministic output for the same input and seed value. Useful
   * for testing and debugging. If null, a random seed is used.
   */
  var rngSeed: Long? = null,
  /**
   * A JSONSchema constraint for the generation. Recommend to use [setResponseFormatType] for
   * setting this value.
   *
   * When set, the schema is automatically injected into the system prompt (if not already present)
   * to provide semantic guidance to the model, in addition to the structural constraint applied
   * during token generation. This dual approach ensures LFM models generate responses that both
   * conform to the schema structure and contain semantically appropriate content. Set
   * [injectSchemaIntoPrompt] to false to disable the prompt injection and use only the structural
   * constraint.
   *
   * **Note on token usage**: The JSON schema is appended to the system message, which increases the
   * prompt token count. Large schemas (1KB+) will consume more of the available context window.
   * Consider this when designing your data structures for constrained generation.
   */
  var jsonSchemaConstraint: String? = null,
  /*
   * Function call parse.
   *
   * This parser will handle the function call contents and parse them into a list of
   * [ai.liquid.leap.function.LeapFunctionCall]. If it set to null, the function calls won't be
   * parsed. The default value is [LFMFunctionCallParser].
   */
  var functionCallParser: LeapFunctionCallParser? = LFMFunctionCallParser(),
  /**
   * Whether to inject the JSON schema constraint into the system message.
   *
   * When true (default), the schema is appended to the system message to provide semantic guidance
   * to the model. When false, the schema is only applied as a structural constraint during token
   * generation (like llama-server's grammar mode).
   *
   * Set to false if you want behavior matching llama-server, or if the schema injection is
   * consuming too many prompt tokens.
   */
  var injectSchemaIntoPrompt: Boolean = true,
  /**
   * Maximum number of tokens to generate.
   *
   * When set, the model will stop generating after producing this many tokens (not counting prompt
   * tokens). If null, the model generates until it produces an end-of-sequence token or reaches the
   * context window limit.
   *
   * This is useful for controlling response length and cost, especially with structured output
   * where you know the expected response size.
   */
  var maxTokens: Int? = null,
  /**
   * Whether to inline thinking/reasoning chunks with normal text responses.
   *
   * When true, thinking content will be emitted as regular
   * [ai.liquid.leap.message.MessageResponse.Chunk] responses with <think></think> tags intact,
   * rather than as separate [ai.liquid.leap.message.MessageResponse.ReasoningChunk] responses. The
   * final [ChatMessage.reasoningContent] will still be populated separately.
   *
   * Default is false (thinking chunks are emitted separately).
   */
  var inlineThinkingTags: Boolean = false,
  /**
   * Whether to enable thinking/reasoning mode in the inference engine.
   *
   * When true, the engine will generate reasoning tokens (e.g., `<think>` blocks) before producing
   * the final response. These are emitted as
   * [ai.liquid.leap.message.MessageResponse.ReasoningChunk] responses (or inlined if
   * [inlineThinkingTags] is also true).
   *
   * Default is false (no reasoning output).
   */
  var enableThinking: Boolean = false,
  /** Extra configuration. Internal use only. */
  var extras: String? = null,
) {
  /**
   * Setting the response format to a certain data class type. The provided class type should be a
   * data class annotated with [ai.liquid.leap.structuredoutput.Generatable]
   */
  inline fun <reified T : Any> setResponseFormatType() {
    this.jsonSchemaConstraint = JSONSchemaGenerator.getJSONSchema<T>()
  }

  companion object {
    /** Instruction prefix for JSON schema guidance in system prompts. */
    private const val SCHEMA_INSTRUCTION_PREFIX =
      "Follow the JSON schema below for your response:\n"

    /** Create a [GenerationOptions] object with build actions. */
    fun build(buildAction: GenerationOptions.() -> Unit): GenerationOptions {
      val options = GenerationOptions()
      options.buildAction()
      return options
    }

    /**
     * Normalizes a JSON schema by removing all non-significant whitespace.
     *
     * This allows comparison of schemas that may differ only in formatting (e.g., compact vs
     * pretty-printed with indentation). Preserves whitespace within string values.
     *
     * @param schema The JSON schema string to normalize
     * @return Normalized schema with whitespace removed between structural elements
     */
    private fun normalizeJsonSchema(schema: String): String {
      val result = StringBuilder()
      var inString = false
      var escapeNext = false
      var i = 0

      while (i < schema.length) {
        val char = schema[i]

        when {
          escapeNext -> {
            result.append(char)
            escapeNext = false
          }
          char == '\\' && inString -> {
            result.append(char)
            escapeNext = true
          }
          char == '"' -> {
            result.append(char)
            inString = !inString
          }
          inString -> {
            result.append(char)
          }
          char.isWhitespace() -> {
            // Skip whitespace outside of strings
          }
          else -> {
            result.append(char)
          }
        }
        i++
      }

      return result.toString()
    }

    /**
     * Checks if a JSON schema instruction should be appended to the given text.
     *
     * Returns the schema instruction text if it should be added, or null if not needed. This
     * ensures LFM models receive both structural constraint and semantic guidance.
     *
     * Performs whitespace-normalized comparison to detect schemas that differ only in formatting
     * (e.g., compact vs pretty-printed), avoiding duplicate injection when a schema was previously
     * embedded with different indentation.
     *
     * @param existingText The existing text content to check
     * @param schema The JSON schema constraint to potentially add
     * @return The schema instruction to append, or null if not needed
     */
    internal fun getSchemaInstructionIfNeeded(existingText: String, schema: String): String? {
      // First check for exact match (fast path)
      if (existingText.contains(SCHEMA_INSTRUCTION_PREFIX + schema)) {
        return null
      }

      // Check if instruction prefix exists and schema is present with different formatting
      if (existingText.contains(SCHEMA_INSTRUCTION_PREFIX)) {
        val normalizedSchema = normalizeJsonSchema(schema)
        val normalizedExistingText = normalizeJsonSchema(existingText)
        if (normalizedExistingText.contains(normalizedSchema)) {
          return null // Schema already present with different formatting
        }
      }

      return "\n\n$SCHEMA_INSTRUCTION_PREFIX$schema"
    }

    /**
     * Injects JSON schema into the system message of a conversation history.
     *
     * This ensures LFM models receive both structural constraint and semantic guidance. The schema
     * is only added if it's not already present. If no system message exists, one is automatically
     * created with the schema.
     *
     * @param messages The conversation history messages
     * @param schema The JSON schema constraint to inject, or null to skip injection
     * @return Modified message list with schema injected, or original list if no injection needed
     */
    internal fun injectSchemaIntoSystemMessage(
      messages: List<ChatMessage>,
      schema: String?,
    ): List<ChatMessage> {
      if (schema.isNullOrBlank()) return messages

      val systemMessageIndex = messages.indexOfFirst { it.role == ChatMessage.Role.SYSTEM }

      // Case 1: No system message exists - create one with the schema
      if (systemMessageIndex < 0) {
        val newSystemMessage =
          ChatMessage(
            role = ChatMessage.Role.SYSTEM,
            content = listOf(ChatMessageContent.Text("$SCHEMA_INSTRUCTION_PREFIX$schema")),
          )
        // Insert at the beginning of the message list
        return listOf(newSystemMessage) + messages
      }

      // Case 2: System message exists - append schema if not already present
      val systemMessage = messages[systemMessageIndex]

      // Extract existing text content
      // Use empty separator to preserve original text structure (important when schema
      // instruction is split across multiple Text content items via createConversationFromHistory)
      val existingText =
        systemMessage.content.filterIsInstance<ChatMessageContent.Text>().joinToString("") {
          it.text
        }

      // Check if schema should be added
      val schemaInstruction =
        getSchemaInstructionIfNeeded(existingText, schema)
          ?: return messages // Schema already present, return original

      // Create modified system message with schema appended
      val modifiedContent = systemMessage.content + ChatMessageContent.Text(schemaInstruction)
      val modifiedSystemMessage = systemMessage.copy(content = modifiedContent)

      // Return new list with modified system message
      return messages.toMutableList().also { it[systemMessageIndex] = modifiedSystemMessage }
    }
  }
}
