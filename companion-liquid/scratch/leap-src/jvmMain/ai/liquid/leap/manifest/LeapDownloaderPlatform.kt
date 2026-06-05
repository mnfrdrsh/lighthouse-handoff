package ai.liquid.leap.manifest

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner

internal actual suspend fun LeapDownloader.platformLoadModel(
  modelName: String,
  quantizationType: String,
  modelLoadingOptions: ModelLoadingOptions?,
  generationTimeParameters: GenerationTimeParameters?,
  forceDownload: Boolean,
  progress: (ProgressData) -> Unit,
): ModelRunner? = null
