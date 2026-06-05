@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap

import co.touchlab.kermit.Logger
import java.io.File
import kotlin.math.ceil

/**
 * JVM implementation of CpuThreadAdvisor with container awareness and memory-based decisions.
 *
 * This advisor detects:
 * - cgroups v1/v2 CPU quotas (Docker/Kubernetes limits)
 * - Available heap memory constraints
 * - Falls back gracefully to Runtime.availableProcessors() on bare metal
 *
 * Thread count is determined by: min(cpuLimit, memoryLimit, MAX_SUPPORTED_THREADS)
 */
actual object CpuThreadAdvisor {
  // Thread count limits
  private const val MAX_SUPPORTED_THREADS = 4
  private const val MIN_THREADS = 1
  private const val FALLBACK_THREAD_COUNT = 2

  // Memory thresholds
  private const val MEMORY_PER_THREAD_MB = 150L // Conservative estimate for LFM inference
  private const val MEMORY_SAFETY_MARGIN = 0.8 // Use 80% of max memory

  // cgroups paths
  private const val CGROUP_V2_CPU_MAX = "/sys/fs/cgroup/cpu.max"
  private const val CGROUP_V2_CONTROLLERS = "/sys/fs/cgroup/cgroup.controllers"
  private const val CGROUP_V1_QUOTA = "/sys/fs/cgroup/cpu/cpu.cfs_quota_us"
  private const val CGROUP_V1_PERIOD = "/sys/fs/cgroup/cpu/cpu.cfs_period_us"

  private val logger = Logger.withTag(CpuThreadAdvisor::class.simpleName ?: "CpuThreadAdvisor")

  private enum class CgroupVersion {
    V1,
    V2,
    NONE,
  }

  /**
   * Returns the recommended number of CPU threads for model inference.
   *
   * The recommendation considers:
   * - Container CPU limits (cgroups v1/v2)
   * - Available heap memory (150MB per thread threshold)
   * - Maximum supported threads (4)
   *
   * @return Thread count between 1 and 4
   */
  actual fun getRecommendedThreadCount(): Int {
    logger.d { "Starting thread count recommendation" }

    // 1. CPU detection
    val effectiveCpuCount = calculateEffectiveCpuCount()
    logger.d { "Effective CPU count: $effectiveCpuCount" }

    // 2. Memory limit (with fallback)
    val memoryBasedLimit =
      try {
        calculateMemoryBasedThreadLimit()
      } catch (e: Exception) {
        logger.w(e) { "Failed to calculate memory limit, ignoring constraint" }
        MAX_SUPPORTED_THREADS
      }

    // 3. Apply constraints
    val recommendedThreads =
      minOf(effectiveCpuCount, memoryBasedLimit, MAX_SUPPORTED_THREADS).coerceAtLeast(MIN_THREADS)

    // 4. Log summary
    logger.i {
      """Thread recommendation: $recommendedThreads
       CPU limit: $effectiveCpuCount
       Memory limit: $memoryBasedLimit
       Max supported: $MAX_SUPPORTED_THREADS"""
        .trimIndent()
    }

    return recommendedThreads
  }

  /**
   * Detects which cgroup version is in use.
   *
   * @return CgroupVersion.V2, V1, or NONE (bare metal)
   */
  private fun detectCgroupVersion(): CgroupVersion {
    // cgroups v2: unified hierarchy with cgroup.controllers file
    if (File(CGROUP_V2_CONTROLLERS).exists()) {
      logger.d { "Detected cgroups v2" }
      return CgroupVersion.V2
    }

    // cgroups v1: separate hierarchies, check for cpu controller
    if (File(CGROUP_V1_QUOTA).exists()) {
      logger.d { "Detected cgroups v1" }
      return CgroupVersion.V1
    }

    logger.d { "No cgroups detected (bare metal)" }
    return CgroupVersion.NONE
  }

  /**
   * Reads a cgroup file safely with error handling.
   *
   * @param path Absolute path to cgroup file
   * @return File contents as trimmed string, or null on error
   */
  @Suppress("TooGenericExceptionCaught")
  private fun readCgroupFile(path: String): String? {
    return try {
      val file = File(path)
      if (file.exists() && file.canRead()) {
        file.readText().trim()
      } else {
        logger.d { "cgroup file not found or not readable: $path" }
        null
      }
    } catch (e: SecurityException) {
      logger.w { "SecurityManager denied access to $path: ${e.message}" }
      null
    } catch (e: Exception) {
      logger.w(e) { "Error reading cgroup file $path" }
      null
    }
  }

  /**
   * Reads CPU quota from cgroups v2.
   *
   * Format: "quota period" (e.g., "250000 100000" = 2.5 cores) or "max period" (unlimited)
   *
   * @return CPU quota as Double (e.g., 2.5), or null if unlimited or error
   */
  private fun readCgroupsV2CpuQuota(): Double? {
    val content = readCgroupFile(CGROUP_V2_CPU_MAX) ?: return null

    val parts = content.split(" ")
    if (parts.size != 2) {
      logger.w { "Invalid cgroups v2 cpu.max format: $content" }
      return null
    }

    val quota = parts[0]
    val period = parts[1].toLongOrNull()

    // "max" means unlimited
    if (quota == "max" || period == null || period == 0L) {
      logger.d { "cgroups v2 CPU quota is unlimited" }
      return null
    }

    val quotaValue = quota.toLongOrNull()
    if (quotaValue == null) {
      logger.w { "Failed to parse cgroups v2 quota: $quota" }
      return null
    }

    val result = quotaValue.toDouble() / period.toDouble()
    logger.d { "cgroups v2 CPU quota: $quotaValue/$period = $result cores" }
    return result
  }

  /**
   * Reads CPU quota from cgroups v1.
   *
   * Reads quota and period from separate files.
   *
   * @return CPU quota as Double (e.g., 2.5), or null if unlimited or error
   */
  private fun readCgroupsV1CpuQuota(): Double? {
    val quotaStr = readCgroupFile(CGROUP_V1_QUOTA) ?: return null
    val periodStr = readCgroupFile(CGROUP_V1_PERIOD) ?: return null

    val quota = quotaStr.toLongOrNull()
    val period = periodStr.toLongOrNull()

    // -1 means unlimited in cgroups v1
    if (quota == null || period == null || quota == -1L || period == 0L) {
      logger.d { "cgroups v1 CPU quota is unlimited or invalid" }
      return null
    }

    val result = quota.toDouble() / period.toDouble()
    logger.d { "cgroups v1 CPU quota: $quota/$period = $result cores" }
    return result
  }

  /**
   * Calculates the effective CPU count considering container limits.
   *
   * Priority: cgroups v2 → cgroups v1 → Runtime.availableProcessors()
   *
   * Fractional quotas are rounded up (e.g., 2.5 → 3) to maximize utilization.
   *
   * @return Effective CPU count capped at available processors
   */
  private fun calculateEffectiveCpuCount(): Int {
    val cgroupVersion = detectCgroupVersion()

    val cpuQuota =
      when (cgroupVersion) {
        CgroupVersion.V2 -> readCgroupsV2CpuQuota()
        CgroupVersion.V1 -> readCgroupsV1CpuQuota()
        CgroupVersion.NONE -> null
      }

    val availableProcessors = Runtime.getRuntime().availableProcessors()

    return if (cpuQuota != null) {
      // Use ceiling to maximize utilization (2.5 cores → 3 threads)
      val ceilingQuota = ceil(cpuQuota).toInt()
      // Don't exceed physical processors
      minOf(ceilingQuota, availableProcessors).coerceAtLeast(MIN_THREADS)
    } else {
      // Bare metal or unlimited container
      availableProcessors
    }
  }

  /**
   * Calculates available heap memory with safety margin.
   *
   * Formula: (maxMemory - usedMemory) * 0.8
   *
   * The 80% safety margin leaves headroom for:
   * - GC overhead
   * - Native memory (JNI inference engine)
   * - Burst allocations during inference
   *
   * @return Available heap memory in bytes
   */
  private fun getAvailableHeapMemory(): Long {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory() // -Xmx value
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    val availableMemory = maxMemory - usedMemory
    return (availableMemory * MEMORY_SAFETY_MARGIN).toLong()
  }

  /**
   * Calculates thread limit based on available heap memory.
   *
   * Uses 150MB per thread threshold, a conservative estimate for LFM inference:
   * - Model context/weights per thread: ~50-100MB
   * - JVM overhead (stack, native): ~10-20MB
   * - Peak burst allocations: ~40-50MB
   *
   * @return Maximum threads based on memory, minimum 1
   */
  private fun calculateMemoryBasedThreadLimit(): Int {
    val availableBytes = getAvailableHeapMemory()
    val availableMB = availableBytes / (1024 * 1024)
    val threadLimit = (availableMB / MEMORY_PER_THREAD_MB).toInt()

    logger.d {
      "Memory: ${availableMB}MB available → ~$threadLimit threads at ${MEMORY_PER_THREAD_MB}MB/thread"
    }

    return threadLimit.coerceAtLeast(MIN_THREADS)
  }
}
