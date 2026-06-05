@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap.inferenceengine

/**
 * Engine-side defaults for the bounded-LRU `CacheOptions` size caps, shared between the `expect`
 * constructor and the resolution chain in `resolvedMaxEntriesDisk`.
 *
 * Keep these in sync with the engine-side defaults in `inference_engine`'s
 * `crates/ie-core/src/loader.rs` (`CacheOptions::default()`); the Rust side is the source of truth
 * because validation happens there.
 */
const val CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_DISK: Int = 4096
const val CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_MEMORY: Int = 256
const val CACHE_OPTIONS_DEFAULT_MAX_BYTES_MEMORY: Long = 512L * 1024 * 1024

expect class EngineOptions(bundlePath: String) {
  var mmprojPath: String?
  var modelAudioDecoderPath: String?
  var audioTokenizerPath: String?
  var cpuThreads: Int?
  var rngSeed: Long?
  var extras: String?
  var chatTemplate: String?
  var cacheOptions: CacheOptions?
  var contextSize: Int?
  var imageMinTokens: Int?
  var imageMaxTokens: Int?
  var useMmap: Boolean?

  /**
   * KV state cache configuration.
   *
   * **`enabled` is the sole gate.** The cache is off when `enabled = false`, on when `enabled =
   * true`. A positive `maxEntries` is *not* sufficient on its own — that intentionally differs from
   * pre-bounded-LRU SDK behavior so a caller's explicit `enabled = false` can never be silently
   * overridden by a stale legacy field. Migrating from `CacheOptions(path, 40)` (which implicitly
   * enabled the cache) requires adding `enabled = true` (or using the
   * `ModelLoadingOptions.cacheOptions(path = ...)` helper).
   *
   * **Disk cap precedence when `enabled = true`** (first non-zero wins):
   * 1. `maxEntriesDisk` if > 0,
   * 2. `maxEntries` if > 0 (legacy disk-cap alias for callers that pre-date the per-tier API),
   * 3. `CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_DISK` (4096) — covers the ergonomic `CacheOptions(path =
   *    ..., enabled = true)` shape.
   *
   * Step 3 exists because the engine validates `enabled = true && maxEntriesDisk == 0` as illegal —
   * "no cap" defeats the safety motivation. The 4096 fallback at the binding boundary keeps the
   * ergonomic shape working without forcing every caller to spell out the cap.
   *
   * `maxEntriesMemory` and `maxBytesMemory` keep their non-zero defaults (256 / 512 MiB) for the
   * same reason; there is no legacy memory-tier field to fall through.
   *
   * **Cross-platform wiring:**
   * - Native (Apple, Linux, MinGW): all fields propagate through cinterop to the engine.
   * - JVM / Android: all fields propagate through the JNI Java AAR's `EngineOptions.CacheOptions`
   *   (which has the bounded-LRU fields directly).
   * - wasmJs: cache config is currently dropped by the runtime — the WASM bridge does not yet
   *   forward `cache_options`. A user-visible warning is logged when `enabled = true` is set on
   *   wasmJs (see `InferenceEngineModelRunner.wasmJs.kt`).
   */
  class CacheOptions(
    path: String,
    maxEntries: Int = 0,
    enabled: Boolean = false,
    maxEntriesDisk: Int = 0,
    maxEntriesMemory: Int = CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_MEMORY,
    maxBytesMemory: Long = CACHE_OPTIONS_DEFAULT_MAX_BYTES_MEMORY,
    diskDisabled: Boolean = false,
  ) {
    // KMP `expect class` constructors cannot declare property parameters; the
    // property surface lives in the body so the `commonMain` extension
    // (`resolvedMaxEntriesDisk`) can access these fields.
    val path: String
    val maxEntries: Int
    val enabled: Boolean
    val maxEntriesDisk: Int
    val maxEntriesMemory: Int
    val maxBytesMemory: Long

    /**
     * Memory-only mode: when `true`, the engine constructs only the in-memory cache tier and skips
     * `KvDiskCache` entirely — useful for benchmarking or for callers who don't need cross-restart
     * persistence and want to avoid the prefill-thread disk-save dispatch entirely. Default `false`
     * preserves the prior "always build the disk tier when `enabled = true`" shape.
     *
     * Inverted name (`Disabled` rather than `Enabled`) so default-zero (`false` for a Bool, the
     * Java/Kotlin / C ABI default) maps to the prior behavior. Older Kotlin SDK clients pre-dating
     * this field continue to work without code changes.
     *
     * Ignored when `enabled = false`. On wasmJs the bridge currently drops the entire
     * `cache_options` block, so this field is also a no-op there until the wasm build is rebuilt
     * against the new `liquid.h`.
     */
    val diskDisabled: Boolean
  }
}

/**
 * Resolve the disk-cap to send to the engine. The chain is:
 * 1. Cache off (returns 0) when `enabled = false`.
 * 2. `maxEntriesDisk` if explicitly set (> 0).
 * 3. Legacy `maxEntries` if it's the only positive cap (callers that pre-date per-tier API).
 * 4. [CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_DISK] (4096) — ergonomic fallback for the
 *    `CacheOptions(path, enabled = true)` shape. Required because the Rust engine validates
 *    `enabled = true && maxEntriesDisk == 0` as illegal.
 *
 * Used by every binding (`jvmMain`/`androidMain` `toJni`, `nativeMain` cinterop, Apple compat) so
 * the resolution stays in one place.
 */
fun EngineOptions.CacheOptions.resolvedMaxEntriesDisk(): Int =
  when {
    !enabled -> 0
    maxEntriesDisk > 0 -> maxEntriesDisk
    maxEntries > 0 -> maxEntries
    else -> CACHE_OPTIONS_DEFAULT_MAX_ENTRIES_DISK
  }
