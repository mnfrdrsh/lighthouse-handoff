@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap

import ai.liquid.leap.manifest.GenerationTimeParameters

expect object LeapInferenceEngine {
  suspend fun loadModel(
    modelPath: String,
    mmprojPath: String? = null,
    audioDecoderPath: String? = null,
    audioTokenizerPath: String? = null,
    options: ModelLoadingOptions? = null,
    generationTimeParameters: GenerationTimeParameters? = null,
  ): ModelRunner
}
