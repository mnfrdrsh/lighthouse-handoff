package ai.liquid.leap.util

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.Locale
import java.util.jar.JarFile

object NativeLibLoader {
  // Intentionally println-only here. The Windows JVM Smoke harness pins its classpath to
  // kotlin-stdlib + this JAR; pulling kermit (or any other logger) into NativeLibLoader
  // would break the smoke's load step with a NoClassDefFoundError. If a logger is ever
  // wanted here, also update .github/workflows/build-artifacts.yml's Windows JVM Smoke step
  // to add the logger jar(s) + transitive deps to its hand-built classpath.
  private var loaded = false
  private var tempDir: File? = null

  private data class PlatformInfo(
    val platform: String,
    val libPrefix: String,
    val libSuffix: String,
  )

  init {
    // Register shutdown hook to clean up temp directory
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          tempDir?.let { dir ->
            try {
              dir.deleteRecursively()
            } catch (_: Exception) {
              // Best effort cleanup - ignore failures during shutdown
            }
          }
        }
      )
  }

  @Synchronized
  fun load() {
    if (loaded) return

    try {
      // Try loading from system library path first
      System.loadLibrary("inference_engine_jni")
      loaded = true
      return
    } catch (_: UnsatisfiedLinkError) {
      // Not found on system path, extract from JAR
    }

    val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
    val osArch = System.getProperty("os.arch").lowercase(Locale.ROOT)

    val info =
      when {
        osName.contains("mac") -> PlatformInfo("darwin", "lib", ".dylib")
        osName.contains("linux") -> {
          val arch = if (osArch.contains("aarch64")) "linux/aarch64" else "linux/x86_64"
          PlatformInfo(arch, "lib", ".so")
        }
        osName.contains("windows") -> {
          val arch = if (osArch.contains("aarch64")) "windows/aarch64" else "windows/x86_64"
          PlatformInfo(arch, "", ".dll")
        }
        else -> throw UnsatisfiedLinkError("Unsupported platform: $osName $osArch")
      }

    tempDir = Files.createTempDirectory("leap_jni_").toFile()

    // Extract every native artifact shipped under jni/<platform>/ in the JAR (or build-tree
    // resources) into the temp dir before any System.load call. The backend's DT_NEEDED
    // entries (libliquid-audio.so, libllama.so.0, libmtmd.so.0, libggml*.so.0) are resolved
    // by ld.so via the backend's DT_RUNPATH ($ORIGIN, set engine-side) — which means siblings
    // must physically exist in the same directory, not just on the JVM classpath. ggml's
    // runtime CPU-variant dispatch likewise dlopen()s libggml-cpu-<microarch>.so by basename
    // and needs them on disk.
    //
    // On macOS the IE build statically links llama / mtmd / liquid-audio / ggml into the
    // backend dylib, so jni/darwin/ ships only two files (jni + backend); the loop is still
    // correct (just smaller). Linux and Windows ship vendor libs as separate shared
    // objects (driven by LIQUID_BUILD_LLAMACPP_CPU_ALL_VARIANTS=ON cascading
    // BUILD_SHARED_LIBS=ON), so jni/<platform>/ contains the full vendor lib set including
    // ggml-cpu microarch variants for runtime CPU dispatch.
    val extractedLibs = extractAllPlatformResources(info.platform, tempDir!!)

    // The libs `System.load` needs to fire on. Other extracted siblings are discovered by
    // ld.so via DT_NEEDED traversal of these entry points; explicitly loading them too
    // would risk symbol collisions.
    val entryPointNames =
      when {
        info.platform == "darwin" ->
          listOf("libinference_engine_llamacpp_backend.dylib", "libinference_engine_jni.dylib")
        info.platform.startsWith("linux") ->
          listOf("libinference_engine_llamacpp_backend.so", "libinference_engine_jni.so")
        info.platform.startsWith("windows") ->
          listOf("inference_engine_llamacpp_backend.dll", "inference_engine_jni.dll")
        else -> listOf("${info.libPrefix}inference_engine_jni${info.libSuffix}")
      }
    val entryPointFiles =
      entryPointNames.map { name ->
        extractedLibs.firstOrNull { it.name == name }
          ?: throw UnsatisfiedLinkError(
            "Required entry-point '$name' not found in jni/${info.platform}/ resources " +
              "(extracted: ${extractedLibs.map { it.name }})"
          )
      }

    // On macOS, the JNI dylib references the backend via @rpath which won't resolve
    // from a temp directory. Patch the install name to use the absolute temp path.
    if (info.platform == "darwin") {
      patchDarwinRpaths(entryPointFiles, tempDir!!)
    }

    // Pre-load every non-entry-point lib so the backend's transitive deps are already
    // in the process's loaded-module cache when System.load fires on the entry points.
    //
    // Why this matters per platform:
    // - Linux: backend's DT_NEEDED list (libliquid-audio.so, libllama.so.0, libmtmd.so.0,
    //   libggml*.so.0) is resolved via DT_RUNPATH=$ORIGIN once the file is in the temp dir.
    //   Pre-loading is harmless — already-loaded libs short-circuit ld.so's NEEDED lookup.
    // - Windows: System.load uses LoadLibraryW (NOT LoadLibraryEx with
    //   LOAD_WITH_ALTERED_SEARCH_PATH) on OpenJDK, so the temp dir is NOT on the DLL
    //   search path. backend.dll's IMPORTS (llama.dll, mtmd.dll, ggml*.dll, liquid-audio.dll)
    //   would fail to resolve. Pre-loading them by absolute path puts them in the
    //   process's loaded-module cache, which Windows checks BEFORE searching the path
    //   for transitive imports — turning the imports into already-loaded references.
    //   Also covers ggml-cpu-<microarch>.dll variants that ggml dlopen()s by basename
    //   at runtime: pre-loading by absolute path puts them in the cache so the basename
    //   lookup later finds them.
    // - macOS: only 2 dylibs ship in jni/darwin/ (vendor libs whole-archive merged into
    //   the backend), so this loop has nothing to do — but the patchDarwinRpaths above
    //   is still required because the JNI dylib's @rpath/backend reference doesn't
    //   resolve from a temp dir without the install_name rewrite.
    //
    // Multi-pass retry handles unknown topology — each pass loads what's now resolvable
    // (its deps were loaded in a prior pass). Stops when no progress is made.
    val pendingDeps = (extractedLibs - entryPointFiles.toSet()).toMutableList()
    var lastFailureCount = -1
    while (pendingDeps.isNotEmpty() && pendingDeps.size != lastFailureCount) {
      lastFailureCount = pendingDeps.size
      val stillPending = mutableListOf<File>()
      for (lib in pendingDeps) {
        try {
          System.load(lib.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
          stillPending.add(lib)
        }
      }
      pendingDeps.clear()
      pendingDeps.addAll(stillPending)
    }
    if (pendingDeps.isNotEmpty()) {
      // Lenient: a sibling that fails to load may still be optional (e.g. a CPU variant
      // that ggml's runtime dispatch never selects on this hardware). Log and continue;
      // the entry-point load below will surface a real error if it actually needs the
      // missing symbol.
      System.err.println(
        "NativeLibLoader: could not pre-load ${pendingDeps.size} dependency lib(s): ${pendingDeps.map { it.name }}"
      )
    }

    entryPointFiles.forEach { lib ->
      try {
        System.load(lib.absolutePath)
      } catch (e: UnsatisfiedLinkError) {
        if (lib == entryPointFiles.last()) {
          throw e
        } else {
          System.err.println(
            "NativeLibLoader: dependency library ${lib.name} failed to load: ${e.message}"
          )
        }
      }
    }

    loaded = true
  }

  /**
   * Extract every native resource under `jni/<platform>/` into [tempDir]. Walks the JAR (or
   * build-tree directory) backing this class so the loader doesn't need a hard-coded list of vendor
   * sibling libraries — when the IE build adds (or removes) a `libggml-cpu-<microarch>.so` variant,
   * the loader picks it up automatically.
   *
   * Returns the list of extracted [File]s. Order is unspecified — callers that need a specific load
   * order should look up by name.
   *
   * If the resource backing cannot be enumerated (e.g. a custom classloader that exposes resources
   * via getResource() but doesn't surface a JAR path), returns an empty list. The caller will then
   * fall back to loading from the system library path.
   */
  private fun extractAllPlatformResources(platform: String, tempDir: File): List<File> {
    val resourcePrefix = "jni/$platform/"
    val source = javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }

    val resourceNames: List<String> =
      when {
        source == null -> emptyList()
        source.isFile && source.name.endsWith(".jar") -> {
          JarFile(source).use { jar ->
            jar
              .entries()
              .asSequence()
              .filter { entry ->
                entry.name.startsWith(resourcePrefix) &&
                  !entry.isDirectory &&
                  entry.name != resourcePrefix
              }
              .map { it.name.removePrefix(resourcePrefix) }
              .filter { !it.contains('/') }
              .toList()
          }
        }
        source.isDirectory -> {
          val dir = File(source, resourcePrefix)
          if (dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
          } else emptyList()
        }
        else -> emptyList()
      }

    if (resourceNames.isEmpty()) {
      System.err.println(
        "NativeLibLoader: no native resources found under $resourcePrefix in ${source?.absolutePath ?: "<unknown source>"}; defaulting to host system library lookup"
      )
      return emptyList()
    }

    return resourceNames.mapNotNull { libName ->
      try {
        extractLibrary(libName, PlatformInfo(platform, "", ""), tempDir)
      } catch (e: UnsatisfiedLinkError) {
        // A jni/<platform>/ entry that vanishes between enumeration and extraction is only
        // possible under unusual classloader setups; log and continue, the entry-point load
        // will surface a more actionable error if the missing file matters.
        System.err.println("NativeLibLoader: could not extract $libName: ${e.message}")
        null
      }
    }
  }

  /**
   * Patches @rpath references in extracted macOS dylibs to use absolute paths.
   *
   * The JNI dylib references the backend via `@rpath/libinference_engine_llamacpp_backend.dylib`,
   * but there is no LC_RPATH entry, so macOS cannot resolve it from a temp directory. This uses
   * `install_name_tool` to rewrite @rpath references to absolute paths in the temp directory.
   */
  private fun patchDarwinRpaths(libs: List<File>, tempDir: File) {
    for (lib in libs) {
      // Find all @rpath dependencies in this dylib
      try {
        val otoolProcess =
          ProcessBuilder("otool", "-L", lib.absolutePath).redirectErrorStream(true).start()
        val otoolOutput = otoolProcess.inputStream.bufferedReader().readText()
        otoolProcess.waitFor()

        for (line in otoolOutput.lines()) {
          val trimmed = line.trim()
          if (trimmed.startsWith("@rpath/")) {
            val depName = trimmed.substringBefore(" (")
            val fileName = depName.removePrefix("@rpath/")
            val absolutePath = File(tempDir, fileName).absolutePath
            // Only patch if the dependency file exists in our temp dir
            if (File(tempDir, fileName).exists()) {
              val process =
                ProcessBuilder(
                    "install_name_tool",
                    "-change",
                    depName,
                    absolutePath,
                    lib.absolutePath,
                  )
                  .redirectErrorStream(true)
                  .start()
              process.waitFor()
            }
          }
        }
      } catch (_: Exception) {
        // Best effort - if install_name_tool isn't available, loading may still
        // work if the libraries happen to be found via other means
      }
    }
  }

  private fun extractLibrary(libName: String, info: PlatformInfo, tempDir: File): File {
    val tempFile = File(tempDir, libName)
    try {
      val inputStream = findLibraryResource(libName, info.platform)
      tempFile.outputStream().use { outputStream -> inputStream.use { it.copyTo(outputStream) } }
    } catch (e: UnsatisfiedLinkError) {
      // If we can't find the library, we just create an empty file or handle it in the caller
      // For now, we'll rethrow it, but the caller of extractLibrary might need to handle it
      throw e
    }

    return tempFile
  }

  private fun findLibraryResource(libName: String, platform: String): InputStream {
    // Try multiple resource paths
    val searchPaths = buildList {
      add("/$libName")
      add("/jni/$platform/$libName")

      // Try alternative names (without version suffix like .0)
      if (libName.contains(".0")) {
        val altName = libName.replace(".0", "")
        add("/$altName")
        add("/jni/$platform/$altName")
      }

      // On Darwin, try without lib prefix
      if (platform == "darwin" && libName.startsWith("lib")) {
        val noPrefix = libName.removePrefix("lib")
        add("/$noPrefix")
        add("/jni/$platform/$noPrefix")
      }
    }

    for (path in searchPaths) {
      javaClass.getResourceAsStream(path)?.let {
        return it
      }
    }

    throw UnsatisfiedLinkError(
      "Could not find library '$libName' in JAR (searched: ${searchPaths.joinToString()})"
    )
  }
}
