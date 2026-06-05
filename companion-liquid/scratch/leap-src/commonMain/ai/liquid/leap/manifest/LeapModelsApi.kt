package ai.liquid.leap.manifest

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Response from the Leap models API */
@Serializable data class LeapModelsResponse(val models: List<LeapApiModel>)

/** A model available from the Leap API */
@Serializable
data class LeapApiModel(
  /** Display name of the model */
  val name: String,
  /** Model identifier (e.g., "lfm2-1.2b") */
  @SerialName("model_slug") val modelSlug: String,
  /** Description of the model */
  val description: String? = null,
  /** List of available quantization slugs */
  @SerialName("quantization_slugs") val quantizationSlugs: List<String>,
  /** List of quantization type names (e.g., "Q4_0", "Q8_0") */
  @SerialName("quantization_types") val quantizationTypes: List<String>,
  /** Organization that published the model */
  val organization: String? = null,
  /** Model size (e.g., "1.2B", "700M") */
  val size: String? = null,
  /** Supported platforms */
  val platform: List<String>? = null,
)

/** Client for fetching available models from the Leap API */
class LeapModelsApi(
  private val baseUrl: String = "https://leap.liquid.ai",
  httpClient: HttpClient? = null,
) : AutoCloseable {
  private val logger: Logger = Logger.withTag("LeapModelsApi")

  private val client: HttpClient =
    httpClient
      ?: HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

  /**
   * Fetch the list of available models from the Leap API.
   *
   * This function is safe to call from the main thread as it automatically switches to the IO
   * dispatcher for network operations.
   *
   * @param inferenceEngine Filter by inference engine (default: "LLAMACPP")
   * @return List of available models with their quantization options
   */
  suspend fun fetchAvailableModels(inferenceEngine: String = "LLAMACPP"): List<LeapApiModel> {
    return try {
      withContext(ioDispatcher) {
        val url = "$baseUrl/api/apollo/models?inference_engine=$inferenceEngine"
        logger.d { "Fetching models from: $url" }
        val response: LeapModelsResponse = client.get(url).body()
        logger.d { "Fetched ${response.models.size} models" }
        response.models
      }
    } catch (e: Exception) {
      logger.e(e) { "Failed to fetch models from API" }
      throw e
    }
  }

  /** Close the HTTP client when done */
  override fun close() {
    client.close()
  }
}
