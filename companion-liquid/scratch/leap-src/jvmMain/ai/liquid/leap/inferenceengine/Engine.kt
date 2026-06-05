@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap.inferenceengine

actual class Engine(private val jniEngine: ai.liquid.inference_engine.Engine) {
  actual val modelId: String?
    get() = jniEngine.getModelId()

  actual fun destroy() = jniEngine.destroy()

  actual fun generate(messages: List<Message>, options: GenerateOptions): GenerationStopReason {
    val jniMessages = messages.map { it.toJni() }
    val jniStopReason = jniEngine.generate(jniMessages, options.toJni())
    return GenerationStopReason.fromJni(jniStopReason)
  }

  fun getPromptTokensSize(messages: List<Message>, addBosToken: Boolean): Int {
    val jniMessages = messages.map { it.toJni() }
    return jniEngine.getPromptTokensSize(jniMessages, addBosToken)
  }

  actual companion object {
    actual fun createFromOptions(options: EngineOptions): Engine =
      Engine(ai.liquid.inference_engine.Engine.createFromOptions(options.toJni()))
  }
}
