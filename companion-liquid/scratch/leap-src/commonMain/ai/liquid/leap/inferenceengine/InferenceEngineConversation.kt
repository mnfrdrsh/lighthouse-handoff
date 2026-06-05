package ai.liquid.leap.inferenceengine

import ai.liquid.leap.Conversation
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.function.LeapFunction
import ai.liquid.leap.message.ChatMessage
import kotlinx.coroutines.sync.Mutex

class InferenceEngineConversation(
  override val modelRunner: ModelRunner,
  history: List<ChatMessage>,
) : Conversation {
  internal val internalHistory: MutableList<ChatMessage> = history.toMutableList()
  override val generatingLock: Mutex = Mutex()
  internal val internalFunctions: MutableList<LeapFunction> = mutableListOf()

  override val history: List<ChatMessage>
    get() {
      return internalHistory.toList()
    }

  override val functions: List<LeapFunction>
    get() = internalFunctions.toList()

  override fun appendToHistory(message: ChatMessage) {
    internalHistory.add(message)
  }

  override fun removeLastMessage() {
    if (internalHistory.isNotEmpty()) {
      internalHistory.removeAt(internalHistory.lastIndex)
    }
  }

  override fun registerFunction(function: LeapFunction) {
    internalFunctions.add(function)
  }
}
