@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap.inferenceengine

import ai.liquid.inference_engine.EngineOptions as JniEngineOptions

actual class EngineOptions actual constructor(private val bundlePath: String) {
  actual var mmprojPath: String? = null
  actual var modelAudioDecoderPath: String? = null
  actual var audioTokenizerPath: String? = null
  actual var cpuThreads: Int? = null
  actual var rngSeed: Long? = null
  actual var extras: String? = null
  actual var chatTemplate: String? = null
  actual var cacheOptions: CacheOptions? = null
  actual var contextSize: Int? = null
  actual var imageMinTokens: Int? = null
  actual var imageMaxTokens: Int? = null
  actual var useMmap: Boolean? = null

  actual class CacheOptions
  actual constructor(
    actual val path: String,
    actual val maxEntries: Int,
    actual val enabled: Boolean,
    actual val maxEntriesDisk: Int,
    actual val maxEntriesMemory: Int,
    actual val maxBytesMemory: Long,
    actual val diskDisabled: Boolean,
  ) {
    /**
     * Build the JNI-side `CacheOptions` from this Kotlin instance. Pass-through: the JNI Java AAR
     * exposes all bounded-LRU fields directly. `enabled` is the sole gate (see commonMain
     * `resolvedMaxEntriesDisk` for disk-cap precedence).
     */
    fun toJni(): JniEngineOptions.CacheOptions =
      JniEngineOptions.CacheOptions(
        path,
        maxEntries,
        enabled,
        resolvedMaxEntriesDisk(),
        maxEntriesMemory,
        maxBytesMemory,
        diskDisabled,
      )
  }

  fun toJni(): JniEngineOptions =
    JniEngineOptions(
      bundlePath = bundlePath,
      rngSeed = rngSeed,
      cacheOptions = cacheOptions?.toJni(),
      cpuThreads = cpuThreads,
      chatTemplate = chatTemplate,
      mmprojPath = mmprojPath,
      contextSize = contextSize,
      imageMinTokens = imageMinTokens,
      imageMaxTokens = imageMaxTokens,
      modelAudioDecoderPath = modelAudioDecoderPath,
      audioTokenizerPath = audioTokenizerPath,
      useMmap = useMmap,
      extras = extras,
    )
}
