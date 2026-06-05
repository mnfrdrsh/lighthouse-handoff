package ai.liquid.leap.manifest

import ai.liquid.leap.GenerationOptions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Data models for deserializing the inference configuration defined by schema.json.
 *
 * Implements custom polymorphism: `load_time_parameters` is decoded based on the sibling field
 * `inference_type` (e.g. "llama.cpp/text-to-text", "llama.cpp/image-to-text",
 * "llama.cpp/lfm2-audio-v1").
 */
@Serializable(with = SchemaSerializer::class)
data class Manifest(
  @SerialName("schema_version") val schemaVersion: String,
  @SerialName("inference_type") val inferenceType: String,
  @SerialName("load_time_parameters") val loadTimeParameters: LoadTimeParameters,
  @SerialName("generation_time_parameters")
  val generationTimeParameters: GenerationTimeParameters? = null,
  @SerialName("original_url") val originalUrl: String? = null,
  @SerialName("path_on_disk") val pathOnDisk: String? = null,
)

/** Base type for variant-specific load time parameters. */
@Serializable
sealed interface LoadTimeParameters {
  val chatTemplate: String?
  val model: String
}

@Serializable
@SerialName("llama.cpp/text-to-text")
data class TextToTextLoadParams(
  @SerialName("chat_template") override val chatTemplate: String? = null,
  @SerialName("model") override val model: String,
) : LoadTimeParameters

@Serializable
@SerialName("llama.cpp/image-to-text")
data class ImageToTextLoadParams(
  @SerialName("chat_template") override val chatTemplate: String? = null,
  @SerialName("model") override val model: String,
  @SerialName("multimodal_projector") val multimodalProjector: String,
) : LoadTimeParameters

@Serializable
@SerialName("llama.cpp/lfm2-audio-v1")
data class Lfm2AudioV1LoadParams(
  @SerialName("chat_template") override val chatTemplate: String? = null,
  @SerialName("model") override val model: String,
  @SerialName("multimodal_projector") val multimodalProjector: String,
  @SerialName("audio_decoder") val audioDecoder: String,
  @SerialName("audio_tokenizer") val audioTokenizer: String,
) : LoadTimeParameters

@Serializable
data class GenerationTimeParameters(
  @SerialName("sampling_parameters") val samplingParameters: SamplingParameters? = null,
  @SerialName("number_of_decoding_threads") val numberOfDecodingThreads: Int? = null,
)

@Serializable
data class SamplingParameters(
  val temperature: Double? = null,
  @SerialName("top_p") val topP: Double? = null,
  @SerialName("min_p") val minP: Double? = null,
  @SerialName("repetition_penalty") val repetitionPenalty: Double? = null,
  @SerialName("top_k") val topK: Int? = null,
)

/**
 * Custom serializer that inspects `inference_type` to choose the correct subtype for
 * `load_time_parameters`.
 */
object SchemaSerializer : KSerializer<Manifest> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("ai.liquid.manifest.Schema", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Manifest {
    val jsonDecoder =
      decoder as? JsonDecoder ?: error("SchemaSerializer can only be used with JSON")
    val json = jsonDecoder.json

    val obj = jsonDecoder.decodeJsonElement().jsonObject

    val schemaVersion =
      obj["schema_version"]?.jsonPrimitive?.content?.trim('"') ?: error("Missing schema_version")
    val inferenceType =
      obj["inference_type"]?.jsonPrimitive?.content?.trim('"') ?: error("Missing inference_type")

    val loadParamsElement: JsonElement =
      obj["load_time_parameters"]?.let { json.decodeFromJsonElement(it) }
        ?: error("Missing load_time_parameters")

    val originalUrl: JsonElement? = obj["original_url"]?.let { json.decodeFromJsonElement(it) }
    val pathOnDisk: JsonElement? = obj["path_on_disk"]?.let { json.decodeFromJsonElement(it) }

    val loadParams: LoadTimeParameters =
      when (inferenceType) {
        "llama.cpp/text-to-text" ->
          json.decodeFromJsonElement<TextToTextLoadParams>(loadParamsElement)
        "llama.cpp/image-to-text" ->
          json.decodeFromJsonElement<ImageToTextLoadParams>(loadParamsElement)
        "llama.cpp/lfm2-audio-v1" ->
          json.decodeFromJsonElement<Lfm2AudioV1LoadParams>(loadParamsElement)
        else -> error("Unsupported inference_type: $inferenceType")
      }

    val generationParams: GenerationTimeParameters? =
      obj["generation_time_parameters"]?.let {
        json.decodeFromJsonElement<GenerationTimeParameters>(it)
      }

    return Manifest(
      schemaVersion = schemaVersion,
      inferenceType = inferenceType,
      loadTimeParameters = loadParams,
      generationTimeParameters = generationParams,
      originalUrl = originalUrl?.jsonPrimitive?.content,
      pathOnDisk = pathOnDisk?.jsonPrimitive?.content,
    )
  }

  override fun serialize(encoder: Encoder, value: Manifest) {
    val jsonEncoder =
      encoder as? JsonEncoder ?: error("SchemaSerializer can only be used with JSON")
    val json = jsonEncoder.json
    val loadParamsElement: JsonElement =
      when (val lp = value.loadTimeParameters) {
        is TextToTextLoadParams -> json.encodeToJsonElement(TextToTextLoadParams.serializer(), lp)
        is ImageToTextLoadParams -> json.encodeToJsonElement(ImageToTextLoadParams.serializer(), lp)
        is Lfm2AudioV1LoadParams -> json.encodeToJsonElement(Lfm2AudioV1LoadParams.serializer(), lp)
      }
    val obj: JsonObject = buildJsonObject {
      put("schema_version", value.schemaVersion)
      put("inference_type", value.inferenceType)
      put("load_time_parameters", loadParamsElement)
      value.generationTimeParameters?.let {
        put(
          "generation_time_parameters",
          json.encodeToJsonElement(GenerationTimeParameters.serializer(), it),
        )
      }
      put("original_url", value.originalUrl)
      put("path_on_disk", value.pathOnDisk)
    }
    jsonEncoder.encodeJsonElement(obj)
  }
}

fun GenerationTimeParameters.toGenerationOptions(): GenerationOptions =
  GenerationOptions(
    temperature = samplingParameters?.temperature?.toFloat(),
    topP = samplingParameters?.topP?.toFloat(),
    minP = samplingParameters?.minP?.toFloat(),
    repetitionPenalty = samplingParameters?.repetitionPenalty?.toFloat(),
    topK = samplingParameters?.topK,
  )
