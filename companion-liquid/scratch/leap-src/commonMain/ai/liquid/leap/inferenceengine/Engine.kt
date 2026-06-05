@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap.inferenceengine

expect class Engine {
  val modelId: String?

  fun destroy()

  fun generate(messages: List<Message>, options: GenerateOptions): GenerationStopReason

  companion object {
    fun createFromOptions(options: EngineOptions): Engine
  }
}
