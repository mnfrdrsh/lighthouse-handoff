package ai.liquid.leap

import ai.liquid.leap.inferenceengine.EngineOptions

/** Options to configure when loading a model. */
data class ModelLoadingOptions(
  /**
   * The random seed for token sampling. Same random seed will result in the same outcome in the
   * generation.
   */
  var randomSeed: Long? = null,
  /** How many cpu threads to use in the generation. */
  var cpuThreads: Int = CpuThreadAdvisor.getRecommendedThreadCount(),
  /** Template to use for a chat experience */
  var chatTemplate: String? = null,
  /**
   * KV cache configuration. Default `null` means "no cache." To enable, construct a
   * `cacheOptions(...)` (helper below) with at least a `path` and `enabled = true`. The
   * `EngineOptions.CacheOptions` shape is exposed directly so callers can configure all bounded-LRU
   * fields (per-tier caps, master switch) without going through a parallel compatibility surface.
   * **Breaking change vs prior versions:** the old `cacheDir: String?` field is removed; migrate
   * via the `cacheOptions(path = ...)` helper for the equivalent 40-entry-disk-budget default.
   */
  var cacheOptions: EngineOptions.CacheOptions? = null,
  /**
   * Maximum context size in tokens. Controls KV cache memory allocation for attention layers.
   * Larger values allow longer conversations but use more memory. Default: 8192 tokens (~96MB KV
   * cache for a typical model).
   */
  var contextSize: Int? = 8192,
  /**
   * Whether to use mmap for model loading (llama.cpp backend only). `null` (default) defers to the
   * engine default of `true`, which is what callers want in nearly every case: mmap'd pages are
   * reclaimable by the OS under memory pressure (instead of forcing OOM-kill), and re-mmapping the
   * same file on a warm reload is satisfied from the OS page cache without disk I/O. Set to `false`
   * to force full-read loading on filesystems where mmap misbehaves (some Android scoped storage
   * paths, certain network mounts).
   */
  var useMmap: Boolean? = null,
  /** Extra configuration. Internal use only. */
  var extras: String? = null,
) {
  companion object {
    /**
     * Disk cap used by the [cacheOptions] convenience helper. Mirrors the historical SDK default
     * (the pre-bounded-LRU `cacheDir = path` shape used 40 entries). This is *not* the engine's own
     * default — that's [ai.liquid.leap.inferenceengine.CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_DISK]
     * (4096), which kicks in when `enabled = true` is set without naming a cap.
     */
    const val LEGACY_CACHE_MAX_ENTRIES_DISK: Int = 40

    /**
     * Convenience constructor for the bounded-LRU cache. Returns an `EngineOptions.CacheOptions`
     * with `enabled = true` and the historical 40-entry disk budget by default — the equivalent of
     * the legacy `ModelLoadingOptions(cacheDir = path, ...)` shape.
     *
     * To disable the cache, set `ModelLoadingOptions.cacheOptions = null` (the default).
     *
     * **Memory-only mode** (`diskDisabled = true`): not exposed by this helper. Construct
     * `EngineOptions.CacheOptions(path = ..., enabled = true, diskDisabled = true)` directly when
     * you want the in-memory cache tier without the disk-backed tier (useful for benchmarking or
     * for callers who don't need cross-restart persistence). See
     * `EngineOptions.CacheOptions.diskDisabled` for the full contract.
     */
    fun cacheOptions(
      path: String,
      maxEntriesDisk: Int = LEGACY_CACHE_MAX_ENTRIES_DISK,
    ): EngineOptions.CacheOptions =
      EngineOptions.CacheOptions(
        path = path,
        maxEntries = maxEntriesDisk,
        enabled = true,
        maxEntriesDisk = maxEntriesDisk,
      )
  }
}
