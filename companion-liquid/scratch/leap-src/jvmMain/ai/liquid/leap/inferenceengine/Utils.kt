package ai.liquid.leap.inferenceengine

import ai.liquid.inference_engine.SamplerParams
import ai.liquid.leap.GenerationOptions

// / Internal utility functions for inference model runner.
internal object Utils {
  /**
   * Get a sampler params for inference engine based on a default sampler params and generation
   * options to override some of the values. Leave all values to default ones if the generation
   * options don't provide that value.
   *
   * @param defaultSamplerParams the default sampler params
   * @param generationOptions the generation options to override the sampler params
   * @return final sampler params for generation
   */
  fun getSamplerParamsWithGenerationOptionOverride(
    defaultSamplerParams: SamplerParams?,
    generationOptions: GenerationOptions?,
  ): SamplerParams? {
    if (generationOptions == null) {
      return defaultSamplerParams
    }
    val base = defaultSamplerParams ?: SamplerParams()
    return SamplerParams(
      temperature = generationOptions.temperature ?: base.temperature,
      topP = generationOptions.topP ?: base.topP,
      minP = generationOptions.minP ?: base.minP,
      repetitionPenalty = generationOptions.repetitionPenalty ?: base.repetitionPenalty,
      topK = generationOptions.topK ?: base.topK,
      rngSeed = generationOptions.rngSeed ?: base.rngSeed,
    )
  }
}
