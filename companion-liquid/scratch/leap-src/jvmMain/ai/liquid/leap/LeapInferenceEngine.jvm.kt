@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap

import ai.liquid.leap.inferenceengine.InferenceEngineModelRunner
import ai.liquid.leap.manifest.GenerationTimeParameters

actual object LeapInferenceEngine {
  actual suspend fun loadModel(
    modelPath: String,
    mmprojPath: String?,
    audioDecoderPath: String?,
    audioTokenizerPath: String?,
    options: ModelLoadingOptions?,
    generationTimeParameters: GenerationTimeParameters?,
  ): ModelRunner {
    return InferenceEngineModelRunner.loadModel(
      modelPath = modelPath,
      mmprojPath = mmprojPath,
      audioDecoderPath = audioDecoderPath,
      audioTokenizerPath = audioTokenizerPath,
      options = options,
      generationTimeParameters = generationTimeParameters,
    )
  }
}
