package ai.liquid.leap.manifest

/**
 * Represents a source model configuration with details about the model files and related
 * information necessary for its usage.
 *
 * @property modelPath The absolute or relative path to the primary model file.
 * @property mmprojPath Optional path to the mmproj file.
 * @property audioDecoderPath Optional path to the audio decoder file.
 * @property audioTokenizerPath Optional path to the audio tokenizer file.
 * @property modelName The model name used for API calls and storage (e.g., "LFM2.5-Audio-1.5B").
 * @property quantizationId Identifier indicating the quantization type.
 * @property downloads A derived property that returns a list of non-null file paths required for
 *   the model, combining all relevant paths such as the model, mmproj, audio decoder, and audio
 *   tokenizer files.
 */
data class ModelSource(
  val modelPath: String,
  val mmprojPath: String? = null,
  val audioDecoderPath: String? = null,
  val audioTokenizerPath: String? = null,
  val modelName: String,
  val quantizationId: String,
) {
  val downloads: List<String>
    get() = listOfNotNull(modelPath, mmprojPath, audioDecoderPath, audioTokenizerPath)
}
