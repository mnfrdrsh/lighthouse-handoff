package ai.liquid.leap.manifest

data class LeapManifestProcessorConfig(
  val targetDir: String = "model_files",
  val validateSha256: Boolean = false,
  val offlineMode: Boolean = false,
)
