package ai.liquid.leap.manifest

data class ProgressData(val bytes: Long, val total: Long) {
  val progress: Float
    get() =
      if (total > 0) {
        (bytes.toFloat() / total)
      } else {
        0f
      }
}
