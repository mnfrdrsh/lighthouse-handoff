package ai.liquid.leap.inferenceengine

class GenerateOptions(
  var samplerParams: SamplerParams? = null,
  var constraints: Constraints? = null,
  var callback: TokenCallback? = null,
) {
  interface TokenCallback {
    fun onToken(token: String?, isSpecial: Boolean)
  }
}
