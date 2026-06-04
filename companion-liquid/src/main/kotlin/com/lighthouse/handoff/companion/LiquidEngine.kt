package com.lighthouse.handoff.companion

// Note: These imports are approximations based on the Liquid LEAP SDK concepts mentioned.
// They may need adjustment based on the exact package structure of leap-sdk 0.10.7.
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.Conversation
import ai.liquid.leap.MessageResponse
import kotlinx.serialization.json.Json
import java.io.File

class LiquidEngine {
    private var runner: ModelRunner? = null
    private var isReady = false
    val modelName = System.getenv("LIQUID_MODEL_ID") ?: "LFM2.5-350M"

    suspend fun initialize() {
        try {
            // Attempt to initialize the ModelRunner
            println("Initializing Liquid Engine with model: $modelName...")
            runner = ModelRunner()
            // In a real scenario, leap-model-downloader would fetch the model first if missing.
            // We assume it's downloaded or the SDK handles it.
            // runner?.load(modelName)
            isReady = true
            println("Liquid Engine initialized successfully.")
        } catch (e: Exception) {
            println("Failed to initialize Liquid Engine: ${e.message}")
            isReady = false
        }
    }

    fun isReady(): Boolean = isReady

    suspend fun analyze(request: AnalyzeRequest): AIAnalysis {
        if (!isReady || runner == null) {
            throw IllegalStateException("MODEL_NOT_READY")
        }

        val prompt = PromptBuilder.buildPrompt(request)
        
        try {
            // Setup conversation
            val conversation = Conversation()
            conversation.addUserMessage(prompt)
            
            // Note: Constrained generation for JSON should ideally be set here if the SDK exposes it.
            val response: MessageResponse = runner!!.generate(conversation)
            val jsonText = response.text.trim()
            
            // Clean up possible markdown fences from the output
            val cleanJson = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString(AIAnalysis.serializer(), cleanJson)
        } catch (e: Exception) {
            println("Failed to parse model output: ${e.message}")
            throw IllegalArgumentException("INVALID_MODEL_OUTPUT")
        }
    }
}
