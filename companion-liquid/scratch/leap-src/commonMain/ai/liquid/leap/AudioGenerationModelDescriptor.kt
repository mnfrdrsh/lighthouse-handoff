package ai.liquid.leap

/**
 * Audio generation models need to have following different components:
 * - Main model
 * - Multimodal projection model
 * - Audio decoder model
 * - (Optional) Audio tokenizer model This data class contains the paths of all models for the
 *   `LeapClient` to load.
 */
data class AudioGenerationModelDescriptor(
  val modelPath: String,
  val mmprojPath: String,
  val audioDecoderPath: String,
  val audioTokenizerPath: String? = null,
)
