package com.lighthouse.handoff.companion

import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.manifest.LeapDownloaderConfig
import ai.liquid.leap.message.MessageResponse
import kotlinx.serialization.json.*
import kotlinx.serialization.SerializationException
import java.io.File

class LiquidEngine {
    private var runner: ModelRunner? = null
    private var isReady = false
    
    val isEnabled = System.getenv("LIQUID_MODEL_ENABLED")?.toBoolean() ?: false
    val cacheDir = System.getenv("LIQUID_MODEL_CACHE_DIR") ?: "D:\\ai-models\\liquid-leap"
    var modelName = System.getenv("LIQUID_MODEL_ID") ?: "LFM2.5-350M"
    val quantization = System.getenv("LIQUID_MODEL_QUANTIZATION") ?: "Q4_K_M"

    suspend fun initialize() {
        println("Starting Lighthouse Handoff Liquid Companion")
        println("Liquid model loading: ${if (isEnabled) "enabled" else "disabled"}")
        println("Model ID: $modelName ($quantization)")

        if (!isEnabled) {
            println("Liquid model loading skipped because LIQUID_MODEL_ENABLED=false")
            return
        }

        try {
            val dir = File(cacheDir)
            if (!dir.exists()) {
                println("Creating cache directory: $cacheDir")
                dir.mkdirs()
            }

            println("Attempting to download/load Liquid model via LeapDownloader...")
            val downloader = LeapDownloader(LeapDownloaderConfig(saveDir = cacheDir))
            
            var lastProgress = -1
            runner = downloader.loadModel(
                modelName = modelName,
                quantizationType = quantization,
                progress = { data ->
                    val pct = (data.progress * 100).toInt()
                    if (pct != lastProgress && pct % 10 == 0) {
                        println("Download/Load progress: $pct% (${data.bytes} / ${data.total} bytes)")
                        lastProgress = pct
                    }
                }
            )
            isReady = true
            println("Liquid model loaded successfully")
        } catch (e: Exception) {
            println("Liquid model load failed: ${e.message}")
            isReady = false
        }
    }

    fun isReady(): Boolean = isReady

    suspend fun analyze(request: AnalyzeRequest): AIAnalysis {
        val requestedModel = request.modelId
        if (runner != null && !requestedModel.isNullOrBlank() && requestedModel != modelName) {
            println("Switching model from $modelName to $requestedModel...")
            try {
                isReady = false
                val downloader = LeapDownloader(LeapDownloaderConfig(saveDir = cacheDir))
                val newRunner = downloader.loadModel(
                    modelName = requestedModel,
                    quantizationType = quantization,
                    progress = { data ->
                        val pct = (data.progress * 100).toInt()
                        println("Download/Load progress for $requestedModel: $pct%")
                    }
                )
                runner = newRunner
                modelName = requestedModel
                isReady = true
                println("Successfully loaded model: $requestedModel")
            } catch (e: Exception) {
                println("Failed to load model $requestedModel: ${e.message}")
                isReady = false
                throw IllegalStateException("Failed to load model $requestedModel: ${e.message}")
            }
        }

        val currentRunner = runner ?: throw IllegalStateException("MODEL_NOT_READY")

        val prompt = PromptBuilder.buildPrompt(request)
        val strictPrompt = "$prompt\n\nReturn ONLY valid JSON matching the expected structure. Do not output anything else."
        
        try {
            println("Running Liquid analysis...")
            val conversation = currentRunner.createConversation()
            val flow = conversation.generateResponse(strictPrompt)
            
            var jsonText = ""
            flow.collect { response ->
                if (response is MessageResponse.Chunk) {
                    jsonText += response.text
                }
            }
            jsonText = jsonText.trim()
            
            println("Raw model output:\n$jsonText")
            
            // Clean up possible markdown fences from the output
            var cleanJson = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            
            // Extract JSON object between first { and last } if needed
            val startIndex = cleanJson.indexOf('{')
            val endIndex = cleanJson.lastIndexOf('}')
            if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1)
            }
            
            val json = Json { ignoreUnknownKeys = true }
            try {
                val rawObj = json.parseToJsonElement(cleanJson).jsonObject
                
                // Helper to safely extract string lists even if model generated objects
                fun extractStringList(element: kotlinx.serialization.json.JsonElement?): List<String> {
                    if (element == null) return emptyList()
                    if (element is kotlinx.serialization.json.JsonArray) {
                        return element.map { item ->
                            if (item is kotlinx.serialization.json.JsonObject) {
                                // Model hallucinated an object instead of string
                                item["description"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                                    ?: item["criteria"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                                    ?: item["limitation"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                                    ?: item.toString()
                            } else if (item is kotlinx.serialization.json.JsonPrimitive) {
                                item.content
                            } else {
                                item.toString()
                            }
                        }
                    } else if (element is kotlinx.serialization.json.JsonPrimitive) {
                        return listOf(element.content) // Model hallucinated single string
                    }
                    return emptyList()
                }

                val execSummary = rawObj["executiveSummary"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: "No summary provided"
                val quickWins = extractStringList(rawObj["quickWins"])
                
                val priorityFixes = rawObj["priorityFixes"]?.let { fixesElement ->
                    if (fixesElement is kotlinx.serialization.json.JsonArray) {
                        fixesElement.mapNotNull { fixItem ->
                            if (fixItem is kotlinx.serialization.json.JsonObject) {
                                PriorityFix(
                                    title = fixItem["title"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: "Fix",
                                    reasoning = fixItem["reasoning"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: "",
                                    instructions = extractStringList(fixItem["instructions"])
                                )
                            } else null
                        }
                    } else emptyList()
                } ?: emptyList()

                val accCriteria = extractStringList(rawObj["acceptanceCriteria"])
                val limitations = extractStringList(rawObj["limitations"])

                val result = AIAnalysis(
                    executiveSummary = execSummary,
                    quickWins = quickWins,
                    priorityFixes = priorityFixes,
                    acceptanceCriteria = accCriteria,
                    limitations = limitations,
                    modelId = modelName
                )
                
                println("Liquid analysis completed")
                return result
            } catch (e: Exception) {
                println("Liquid analysis failed to parse JSON: ${e.message}")
                // Throw an exception that contains the raw output so the client can see it
                throw IllegalArgumentException("Raw Output: $jsonText")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            println("Liquid analysis failed: ${e.message}")
            throw e
        }
    }
}
