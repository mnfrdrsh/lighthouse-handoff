package ai.liquid.leap.inferenceengine

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap

/** Internal bundle config data representation. */
data class BundleConfig(val systemMessage: String?, val samplerParams: SamplerParams?) {
  companion object {
    /** Parse an YAML string to create the bundle config object. */
    fun parse(raw: String): Result<BundleConfig> = runCatching {
      val node = Yaml.default.parseToYamlNode(raw)

      var systemMessage: String? = null
      node.yamlMap.getMap("chat")?.let { section ->
        section.getScalar("system_message")?.let { systemMessage = it.content }
      }

      var samplerConfig: SamplerParams? = null
      node.yamlMap.getMap("sampler")?.let { section ->
        samplerConfig = SamplerParams()
        samplerConfig.temperature = section.getScalar("temperature")?.toFloat()
        samplerConfig.topP = section.getScalar("top_p")?.toFloat()
        samplerConfig.minP = section.getScalar("min_p")?.toFloat()
        samplerConfig.repetitionPenalty = section.getScalar("repetition_penalty")?.toFloat()
        samplerConfig.topK = section.getScalar("top_k")?.toInt()
      }

      val config = BundleConfig(systemMessage, samplerConfig)
      return Result.success(config)
    }
  }
}

/*
 Having this function is to eliminate the inline reified type issue introduced by the new Kaml.
 This implementation is same as the old one but dedicated to map.
*/
private fun YamlMap.getMap(key: String): YamlMap? {
  val node = this.entries.entries.firstOrNull { it.key.content == key }?.value
  if (node == null) {
    return null
  }
  return node as? YamlMap
}
