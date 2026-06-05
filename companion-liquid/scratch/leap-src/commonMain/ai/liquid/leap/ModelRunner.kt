package ai.liquid.leap

import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.MessageResponse

/**
 * A running model. Any model runner implementations need to expose certain methods to allow LEAP
 * SDK to control the model.
 */
interface ModelRunner {
  /**
   * Create a conversation object from this model runner.
   *
   * @param systemPrompt customized system prompt. A default system prompt from the model bundle
   *   will be used if it is null.
   */
  fun createConversation(systemPrompt: String? = null): Conversation

  /**
   * Create a conversation from chat message history.
   *
   * @param history chat history (messages) to invoke the generation
   */
  fun createConversationFromHistory(history: List<ChatMessage>): Conversation

  /**
   * Unload the model from the memory. The model runner and conversations created from this model
   * runner cannot be used anymore after invoking this method.
   */
  suspend fun unload()

  /**
   * Get the prompt tokens size for a list of messages.
   *
   * @param messages the chat history to count tokens for
   * @param addBosToken whether to add the beginning-of-sequence token
   * @return the number of tokens in the prompt
   */
  suspend fun getPromptTokensSize(messages: List<ChatMessage>, addBosToken: Boolean = true): Int

  /** A string to identify the model. */
  val modelId: String

  /** Callbacks to be called when chunks/data is generated */
  interface GenerationCallback {
    /**
     * Callback to invoke when a piece of response is available
     *
     * @param response the generated response from the model
     */
    fun onResponse(response: MessageResponse)

    /**
     * Callback to invoke when an exception or an error is thrown during the generation
     *
     * @param error the error throwable object
     */
    fun onError(error: Throwable)
  }

  /**
   * Handler returned by [generateFromConversation] to control an in-progress generation.
   *
   * Consumers should not use this interface directly. Use [Conversation.generateResponse] instead;
   * cancelling the returned [kotlinx.coroutines.flow.Flow]'s collector automatically calls [stop]
   * via the flow's `awaitClose` block.
   */
  interface GenerationHandler {
    /**
     * Signal the engine to stop generating tokens.
     *
     * The call is best-effort: at least one more token may be delivered before generation halts.
     * The exact threading behaviour depends on the platform:
     * - **Android / JVM**: synchronous — calls `engine.stop()` directly; the JNI layer sets an
     *   atomic flag (`SeqCst`) that is thread-safe and disjoint from generation state.
     * - **iOS / macOS (native)**: synchronous — calls `liquid_inference_engine_stop()` directly;
     *   the native layer is thread-safe so this is safe from any thread.
     * - **wasmJs (Worker-backed)**: non-blocking — sets a shared-memory stop flag or posts a `stop`
     *   message to the Web Worker; the Worker checks the flag between tokens.
     */
    fun stop()
  }

  /**
   * Start generating a response for [conversation] and return a [GenerationHandler] immediately.
   *
   * **Prefer [Conversation.generateResponse]** over calling this method directly. This method is
   * low-level: callers are responsible for thread safety and for calling [GenerationHandler.stop]
   * when the generation should be cancelled.
   *
   * ### Contract for implementors
   * - **Must be non-blocking**: return the [GenerationHandler] before any [callback] fires. All
   *   platforms achieve this by starting inference on a background thread or Worker and returning
   *   immediately (Android/JVM via `scope.launch`; native via a background coroutine; wasmJs via a
   *   Web Worker message).
   * - Callbacks may be invoked from a thread other than the caller's thread.
   * - [GenerationCallback.onResponse] is called for every [MessageResponse.Chunk] and once for the
   *   terminal [MessageResponse.Complete].
   * - [GenerationCallback.onError] is called instead of `onResponse(Complete)` when the engine
   *   encounters an unrecoverable error.
   *
   * @param conversation the conversation whose history is used as the prompt.
   * @param callback receives streaming chunks and the terminal complete/error event.
   * @return a [GenerationHandler] that can stop the in-progress generation.
   */
  suspend fun generateFromConversation(
    conversation: Conversation,
    callback: GenerationCallback,
    generationOptions: GenerationOptions? = null,
  ): GenerationHandler
}
