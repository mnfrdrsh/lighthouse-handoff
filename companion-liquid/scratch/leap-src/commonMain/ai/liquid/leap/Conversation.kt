package ai.liquid.leap

import ai.liquid.leap.function.LeapFunction
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.GenerationFinishReason
import ai.liquid.leap.message.MessageResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex

/** A data instance to hold the conversation context. */
interface Conversation {
  // Internal state of chat history
  val modelRunner: ModelRunner

  // A lock to ensure that no actions can be conducted if the conversation is generating responses.
  val generatingLock: Mutex

  /** The conversation history. */
  val history: List<ChatMessage>

  /** The registered functions for this conversation. */
  val functions: List<LeapFunction>

  /**
   * Append a message to the history without triggering any generation.
   *
   * @param message the message to append
   */
  fun appendToHistory(message: ChatMessage)

  /**
   * Remove the last message from the history. No-ops if the history is empty.
   *
   * Used internally to clean up a dangling user message when generation is cancelled before the
   * assistant produces a [MessageResponse.Complete].
   */
  fun removeLastMessage()

  /** Register a function into the conversation. */
  fun registerFunction(function: LeapFunction)

  /** Register a list of functions into the conversation. */
  fun registerFunctions(functions: List<LeapFunction>) {
    functions.forEach { this.registerFunction(it) }
  }

  /**
   * Generate response with text input from the user.
   *
   * See [generateResponse] for full usage and stop semantics.
   *
   * @param userTextMessage the user's text input
   */
  fun generateResponse(
    userTextMessage: String,
    generationOptions: GenerationOptions? = null,
  ): Flow<MessageResponse> =
    this.generateResponse(
      ChatMessage(ChatMessage.Role.USER, listOf(ChatMessageContent.Text(userTextMessage))),
      generationOptions,
    )

  /**
   * Generate a response to [message] and return a [Flow] of [MessageResponse] events.
   *
   * The message is appended to [history] before generation starts. Each token arrives as a
   * [MessageResponse.Chunk]; when the model finishes a [MessageResponse.Complete] is emitted and
   * the flow closes normally. On error the flow closes with a [LeapGenerationException].
   *
   * ### Stopping generation
   *
   * **Cancel the collecting coroutine.** The flow is built with `callbackFlow`; when the collector
   * is cancelled (or calls a terminal operator that cancels internally, such as `first()`), the
   * flow's `awaitClose` block fires automatically, calls `handler.stop()` on the underlying engine,
   * and releases the generation lock. No additional bookkeeping is required on the caller side.
   *
   * ```kotlin
   * // Collect all tokens until done:
   * conversation.generateResponse("Hello").collect { response -> ... }
   *
   * // Stop after the first token — cancels the rest automatically:
   * val first = conversation.generateResponse("Hello").first()
   *
   * // Stop from a parent scope (e.g. when the user navigates away):
   * val job = scope.launch {
   *   conversation.generateResponse("Hello").collect { ... }
   * }
   * job.cancel() // triggers awaitClose → handler.stop()
   * ```
   *
   * Only one generation can run on a given [Conversation] at a time; concurrent calls block on an
   * internal [Mutex] until the previous generation finishes or is stopped. Use [isGenerating] to
   * check whether a generation is in progress before starting a new one.
   *
   * @param message the message to append and generate a response for.
   */
  fun generateResponse(
    message: ChatMessage,
    generationOptions: GenerationOptions? = null,
  ): Flow<MessageResponse> = callbackFlow {
    generatingLock.lock()
    appendToHistory(message)
    val callback =
      object : ModelRunner.GenerationCallback {
        override fun onResponse(response: MessageResponse) {
          trySend(response)
          if (response is MessageResponse.Complete) {
            // Don't append error responses to history — partial/broken messages corrupt subsequent
            // turns.
            if (response.finishReason != GenerationFinishReason.ERROR) {
              appendToHistory(response.fullMessage)
            }
            channel.close()
          }
        }

        override fun onError(error: Throwable) {
          close(error)
        }
      }
    // All real generateFromConversation implementations are non-blocking (JVM/Android start a
    // background thread internally; wasmJs posts to a Worker) and return a handler immediately.
    // Calling directly without withContext/launch guarantees the handler is captured before
    // awaitClose can run, eliminating dispatcher-boundary races where a synchronous onError
    // callback closes the flow before the handler assignment completes.
    val handler =
      modelRunner.generateFromConversation(this@Conversation, callback, generationOptions)

    awaitClose {
      handler.stop()
      // Note: we intentionally do NOT remove the user message here. The previous
      // implementation tried to remove "dangling" user messages when generation was
      // cancelled, but handler.stop() races with the statsCallback — the callback
      // may fire appendToHistory AFTER awaitClose checks history.last(), causing the
      // user message to be incorrectly removed and breaking multi-turn conversations.
      // Keeping the user message in history is harmless — the next generation will
      // include it in the prefill context.
      generatingLock.unlock()
    }
  }

  /** Whether a generation is in progress */
  val isGenerating: Boolean
    get() = generatingLock.isLocked
}
