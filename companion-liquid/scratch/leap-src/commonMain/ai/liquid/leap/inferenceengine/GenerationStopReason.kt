package ai.liquid.leap.inferenceengine

enum class GenerationStopReason {
  FINISHED,
  INTERRUPTED,
  OUT_OF_CONTEXT,
  UNKNOWN;

  companion object
}
