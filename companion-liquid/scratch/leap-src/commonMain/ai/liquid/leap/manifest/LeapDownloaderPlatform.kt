package ai.liquid.leap.manifest

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner

/**
 * Platform hook for [LeapDownloader.loadModel].
 *
 * Returns a [ModelRunner] on wasmJs (models are fetched via the browser Fetch API and loaded from
 * Emscripten MEMFS), or `null` on all other platforms (which use the standard disk-based
 * implementation).
 */
internal expect suspend fun LeapDownloader.platformLoadModel(
  modelName: String,
  quantizationType: String,
  modelLoadingOptions: ModelLoadingOptions?,
  generationTimeParameters: GenerationTimeParameters?,
  forceDownload: Boolean,
  progress: (ProgressData) -> Unit,
): ModelRunner?
