package ai.liquid.leap.manifest

data class LeapDownloaderConfig(
  val saveDir: String = "leap_models",
  val validateSha256: Boolean = true,
  /** Only for testing: Disables SSL certificate validation (useful for iOS simulator) */
  val disableSslValidation: Boolean = false,
  /** Only for testing: Override the base URL for manifest API (default: https://leap.liquid.ai) */
  val baseUrl: String? = null,
  /** HTTP connect timeout in milliseconds (default: 30 seconds) */
  val connectTimeoutMillis: Long = 30_000,
  /** HTTP socket/read timeout in milliseconds (default: 60 seconds) */
  val socketTimeoutMillis: Long = 60_000,
  /** HTTP request timeout in milliseconds (default: 10 minutes) */
  val requestTimeoutMillis: Long = 600_000,
) {
  init {
    require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
    require(socketTimeoutMillis > 0) { "socketTimeoutMillis must be positive" }
    require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
  }
}
