package ai.liquid.leap

import ai.liquid.leap.manifest.GenerationTimeParameters
import ai.liquid.leap.manifest.ImageToTextLoadParams
import ai.liquid.leap.manifest.Lfm2AudioV1LoadParams
import ai.liquid.leap.manifest.LoadTimeParameters
import ai.liquid.leap.manifest.TextToTextLoadParams
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Main entrypoint of LeapSDK */
object LeapClient {
  /**
   * Load a model from the model bundle file. The app needs to hold the model runner object returned
   * by this function until it doesn't need to interact with the model anymore. If the returned
   * model runner object is not longer alive, all conversations created from this model runner will
   * not able to do generation.
   *
   * This function is safe to call from the main thread.
   *
   * @param modelPath local path of the model bundle file
   * @param options loading options
   * @return model runner object
   * @throws LeapModelLoadingException
   */
  suspend fun loadModel(modelPath: String, options: ModelLoadingOptions? = null): ModelRunner =
    LeapInferenceEngine.loadModel(modelPath = modelPath, options = options)

  /**
   * Load a model from the model bundle file with a multimodal projection model.
   *
   * This function is safe to call from the main thread.
   *
   * @param modelPath local path of the model bundle file
   * @param mmprojPath multimodal projection model file
   * @param options loading options
   * @return model runner object
   * @throws LeapModelLoadingException
   */
  suspend fun loadModel(
    modelPath: String,
    mmprojPath: String,
    options: ModelLoadingOptions? = null,
  ): ModelRunner =
    LeapInferenceEngine.loadModel(modelPath = modelPath, mmprojPath = mmprojPath, options = options)

  /**
   * Load an audio generation model from the model bundle files.
   *
   * This function is safe to call from the main thread.
   *
   * @param model descriptor for audio generation model
   * @param options loading options
   * @return model runner object
   * @throws LeapModelLoadingException
   */
  suspend fun loadModel(
    model: AudioGenerationModelDescriptor,
    options: ModelLoadingOptions? = null,
  ): ModelRunner =
    LeapInferenceEngine.loadModel(
      modelPath = model.modelPath,
      mmprojPath = model.mmprojPath,
      audioDecoderPath = model.audioDecoderPath,
      audioTokenizerPath = model.audioTokenizerPath,
      options = options,
    )

  /**
   * Load an audio generation model from the model bundle files.
   *
   * This function is safe to call from the main thread.
   *
   * @param params LoadTimeParameters from the manifest
   * @param options loading options
   * @return model runner object
   * @throws LeapModelLoadingException
   */
  suspend fun loadModel(
    params: LoadTimeParameters,
    generationParams: GenerationTimeParameters? = null,
    options: ModelLoadingOptions? = null,
  ): ModelRunner =
    when (params) {
      is Lfm2AudioV1LoadParams -> {
        LeapInferenceEngine.loadModel(
          modelPath = params.model,
          mmprojPath = params.multimodalProjector,
          audioDecoderPath = params.audioDecoder,
          audioTokenizerPath = params.audioTokenizer,
          options = options,
          generationTimeParameters = generationParams,
        )
      }
      is ImageToTextLoadParams -> {
        LeapInferenceEngine.loadModel(
          modelPath = params.model,
          mmprojPath = params.multimodalProjector,
          audioDecoderPath = null,
          audioTokenizerPath = null,
          options = options,
          generationTimeParameters = generationParams,
        )
      }
      is TextToTextLoadParams -> {
        LeapInferenceEngine.loadModel(modelPath = params.model, options = options)
      }
    }

  /**
   * Non-throwable version of [loadModel]. If the model fails to load, the exception will be
   * returned as a failure result. This function is safe to call from the main thread.
   *
   * Note: Hidden from ObjC/Swift because [kotlin.Result] is an inline class that is opaque to
   * Swift. Use [loadModel] with try/catch instead.
   *
   * @param bundlePath local path of the model bundle file
   * @return model runner object or an exception wrapped in [Result]
   */
  @OptIn(ExperimentalObjCRefinement::class)
  @HiddenFromObjC
  suspend fun loadModelAsResult(
    bundlePath: String,
    options: ModelLoadingOptions? = null,
  ): Result<ModelRunner> = runCatching { loadModel(bundlePath, options) }
}
