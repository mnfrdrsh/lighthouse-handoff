package ai.liquid.leap.inferenceengine

import ai.liquid.inference_engine.Engine
import ai.liquid.inference_engine.EngineException
import ai.liquid.inference_engine.ExceedContextLengthException
import ai.liquid.inference_engine.GenerateOptions
import ai.liquid.inference_engine.GenerationStopReason
import ai.liquid.inference_engine.JpegContent
import ai.liquid.inference_engine.JsonSchemaConstraint
import ai.liquid.inference_engine.Message
import ai.liquid.inference_engine.PcmF32MonoContent
import ai.liquid.inference_engine.SamplerParams as JniSamplerParams
import ai.liquid.inference_engine.StringContent
import ai.liquid.inference_engine.WavContent
import ai.liquid.leap.Conversation
import ai.liquid.leap.CpuThreadAdvisor
import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapGenerationException
import ai.liquid.leap.LeapGenerationFunctionCallParsingException
import ai.liquid.leap.LeapGenerationPromptExceedContextLengthException
import ai.liquid.leap.LeapModelLoadingException
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.ModelRunner.GenerationCallback
import ai.liquid.leap.ModelRunner.GenerationHandler
import ai.liquid.leap.audio.FloatAudioBuffer
import ai.liquid.leap.function.LFMFunctionCallParser
import ai.liquid.leap.inferenceengine.SamplerParams as SdkSamplerParams
import ai.liquid.leap.manifest.GenerationTimeParameters
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.GenerationFinishReason
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leap.util.NativeLibLoader
import co.touchlab.kermit.Logger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InferenceEngineModelRunner(
  private val inferenceEngine: Engine,
  private val systemPrompt: String?,
  private val samplerParams: JniSamplerParams?,
) : ModelRunner {
  private val logger =
    Logger.withTag(InferenceEngineModelRunner::class.simpleName ?: "InferenceEngineModelRunner")

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val generateMutex: Mutex = Mutex()

  override val modelId: String
    get() {
      try {
        return inferenceEngine.getModelId()
      } catch (e: Exception) {
        logger.w("Failed to retrieve model ID due to exception: $e. Empty string is returned.")
        return ""
      }
    }

  override suspend fun generateFromConversation(
    conversation: Conversation,
    callback: GenerationCallback,
    generationOptions: GenerationOptions?,
  ): GenerationHandler {
    require(conversation is InferenceEngineConversation) {
      "Conversation must be created by InferenceEngineModelRunner"
    }
    val functionCallParser =
      if (generationOptions == null) {
        // Use LFM function call parser by default.
        LFMFunctionCallParser()
      } else {
        generationOptions.functionCallParser
      }
    functionCallParser?.clear()

    // Auto-inject JSON schema into system message (common code)
    val historyWithSchema =
      if (generationOptions?.injectSchemaIntoPrompt != false) {
        GenerationOptions.injectSchemaIntoSystemMessage(
          conversation.internalHistory,
          generationOptions?.jsonSchemaConstraint,
        )
      } else {
        conversation.internalHistory
      }

    val messages: List<Message> =
      historyWithSchema.map {
        var contents =
          it.content.map { contentItem ->
            when (contentItem) {
              is ChatMessageContent.Text -> StringContent(contentItem.text)
              is ChatMessageContent.Image -> JpegContent(contentItem.jpegByteArray)
              is ChatMessageContent.Audio -> WavContent(contentItem.data)
              is ChatMessageContent.AudioPcmF32 ->
                PcmF32MonoContent(contentItem.samples, contentItem.sampleRate)
            }
          }
        if (it.functionCalls != null && it.role != ChatMessage.Role.ASSISTANT) {
          if (functionCallParser == null) {
            throw LeapGenerationFunctionCallParsingException(
              "Function calls are attached in the messages, but parser is missing",
              null,
            )
          }
          val functionCallContent = functionCallParser.dump(it.functionCalls)
          val functionCallContentObject = StringContent(functionCallContent)
          contents = listOf(functionCallContentObject) + contents
        }

        Message(it.role.type, contents)
      }
    scope.launch {
      generateMutex.lock()
      var audioBuffer: FloatAudioBuffer? = null
      val rawTextBuffer = StringBuilder()
      val tokenProcessor =
        TokenProcessor(
          callback = callback,
          functionCallParser = functionCallParser,
          inlineThinking = generationOptions?.inlineThinkingTags ?: false,
        )
      val generateOption =
        GenerateOptions(
          tokenCallback = { chunk, isSpecial ->
            rawTextBuffer.append(chunk)
            tokenProcessor.processToken(chunk, isSpecial)
          },
          samplerParams =
            Utils.getSamplerParamsWithGenerationOptionOverride(samplerParams, generationOptions),
          tools =
            if (conversation.functions.isNotEmpty()) {
              conversation.functions.map {
                ai.liquid.leap.LeapJsonPretty.encodeToString(
                  kotlinx.serialization.json.JsonObject.serializer(),
                  it.toJsonObject(withToolTypeWrapper = true),
                )
              }
            } else {
              null
            },
          constraint = generationOptions?.jsonSchemaConstraint?.let { JsonSchemaConstraint(it) },
          nPredict = generationOptions?.maxTokens,
          enableThinking = generationOptions?.enableThinking ?: false,
          extras = generationOptions?.extras,
          statsCallback = { stats, stopReason ->
            val statsOutput =
              ai.liquid.leap.message.GenerationStats(
                promptTokens = stats.numPromptTokens,
                completionTokens = stats.numGeneratedTokens,
                totalTokens =
                  stats.numPromptTokens + stats.numGeneratedTokens + stats.numCachedPromptTokens,
                tokenPerSecond = stats.tokensPerSecond,
                cachedPromptTokens = stats.numCachedPromptTokens,
              )
            val content: MutableList<ChatMessageContent> =
              mutableListOf(ChatMessageContent.Text(rawTextBuffer.toString()))
            audioBuffer?.let { buffer -> content.add(buffer.makePcmF32AudioContent()) }
            val reasoningContent =
              tokenProcessor.reasoningBuffer.toString().trim().takeIf { it.isNotEmpty() }
            val finishReason =
              when (stopReason) {
                GenerationStopReason.Eos -> GenerationFinishReason.STOP
                GenerationStopReason.ExceedContextWindow -> GenerationFinishReason.EXCEED_CONTEXT
                GenerationStopReason.Interrupted -> GenerationFinishReason.INTERRUPTED
                GenerationStopReason.Constraint -> GenerationFinishReason.CONSTRAINT
                GenerationStopReason.Error -> GenerationFinishReason.ERROR
                else -> GenerationFinishReason.ERROR
              }
            callback.onResponse(
              MessageResponse.Complete(
                fullMessage =
                  ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = content,
                    reasoningContent = reasoningContent,
                    functionCalls = tokenProcessor.functionCallBuffer.ifEmpty { null },
                  ),
                finishReason = finishReason,
                stats = statsOutput,
              )
            )
          },
          audioSamplesCallback = { samples, sampleRate ->
            callback.onResponse(MessageResponse.AudioSample(samples, sampleRate))
            if (audioBuffer == null) {
              audioBuffer = FloatAudioBuffer(sampleRate)
            }
            audioBuffer.add(samples)
          },
        )

      try {
        inferenceEngine.generate(messages, generateOption)
      } catch (e: ExceedContextLengthException) {
        val maxContextLength = inferenceEngine.getState().maxSeqLen
        callback.onError(
          LeapGenerationPromptExceedContextLengthException(
            "The prompt exceeds the max context length of $maxContextLength tokens.",
            e,
          )
        )
      } catch (e: EngineException) {
        callback.onError(LeapGenerationException("Error in generation", e))
      } finally {
        generateMutex.unlock()
      }
    }

    return object : GenerationHandler {
      override fun stop() {
        inferenceEngine.stop()
      }
    }
  }

  override fun createConversation(systemPrompt: String?): InferenceEngineConversation {
    val systemPrompt = systemPrompt ?: this.systemPrompt
    val history =
      if (systemPrompt.isNullOrEmpty()) {
        listOf()
      } else {
        listOf(
          ChatMessage(
            role = ChatMessage.Role.SYSTEM,
            content = listOf(ChatMessageContent.Text(systemPrompt)),
          )
        )
      }
    return InferenceEngineConversation(this, history)
  }

  override fun createConversationFromHistory(history: List<ChatMessage>): Conversation =
    InferenceEngineConversation(this, history)

  override suspend fun getPromptTokensSize(messages: List<ChatMessage>, addBosToken: Boolean): Int {
    val jniMessages: List<Message> =
      messages.map {
        val contents =
          it.content.map { contentItem ->
            when (contentItem) {
              is ChatMessageContent.Text -> StringContent(contentItem.text)
              is ChatMessageContent.Image -> JpegContent(contentItem.jpegByteArray)
              is ChatMessageContent.Audio -> WavContent(contentItem.data)
              is ChatMessageContent.AudioPcmF32 ->
                PcmF32MonoContent(contentItem.samples, contentItem.sampleRate)
            }
          }
        Message(it.role.type, contents)
      }
    return inferenceEngine.getPromptTokensSize(jniMessages, addBosToken)
  }

  override suspend fun unload() {
    // First, signal cancellation to any running jobs
    scope.cancel()

    // Wait for any active generation to complete before destroying the engine
    // This prevents use-after-free if generation is running
    generateMutex.withLock { inferenceEngine.destroy() }
  }

  companion object {
    const val TAG = "InferenceEngineModelRunner"

    fun loadModel(
      modelPath: String,
      mmprojPath: String? = null,
      audioDecoderPath: String? = null,
      audioTokenizerPath: String? = null,
      options: ModelLoadingOptions? = null,
      generationTimeParameters: GenerationTimeParameters? = null,
    ): InferenceEngineModelRunner {
      NativeLibLoader.load()
      val modelFile = File(modelPath)
      checkCanReadBundleFile(modelFile)
      val engineOption = getEngineOptions(modelPath, options)
      engineOption.mmprojPath = mmprojPath
      engineOption.modelAudioDecoderPath = audioDecoderPath
      engineOption.audioTokenizerPath = audioTokenizerPath
      val engine = Engine.createFromOptions(engineOption.toJni())
      val bundleConfigString =
        try {
          loadBundleConfigString(modelFile)
        } catch (_: Exception) {
          null
        }
      val bundleConfig =
        BundleConfig.parse(bundleConfigString ?: "").getOrElse {
          Logger.w(TAG) { "Bundle config is not available in the provided model bundle." }
          BundleConfig(null, null)
        }
      val newBundleConfig = newConfig(generationTimeParameters)
      return InferenceEngineModelRunner(
        engine,
        bundleConfig.systemMessage,
        bundleConfig.samplerParams?.toJni() ?: newBundleConfig?.samplerParams?.toJni(),
      )
    }

    internal fun getEngineOptions(
      bundlePath: String,
      modelLoadingOptions: ModelLoadingOptions?,
    ): EngineOptions {
      val engineOptions = EngineOptions(bundlePath)
      if (modelLoadingOptions != null) {
        engineOptions.cpuThreads = modelLoadingOptions.cpuThreads
        engineOptions.rngSeed = modelLoadingOptions.randomSeed
        engineOptions.extras = modelLoadingOptions.extras
        engineOptions.chatTemplate = modelLoadingOptions.chatTemplate
        engineOptions.contextSize = modelLoadingOptions.contextSize
        engineOptions.useMmap = modelLoadingOptions.useMmap
        // Forward the high-level `cacheOptions` directly to the engine. The user constructs them
        // via `ModelLoadingOptions.cacheOptions(path = ..., ...)` (or null to disable).
        engineOptions.cacheOptions = modelLoadingOptions.cacheOptions
      }
      if (engineOptions.cpuThreads == null) {
        engineOptions.cpuThreads = CpuThreadAdvisor.getRecommendedThreadCount()
      }

      return engineOptions
    }

    private fun checkCanReadBundleFile(bundleFile: File) {
      try {
        if (!(bundleFile.isFile && bundleFile.canRead())) {
          throw LeapModelLoadingException("Cannot open the bundle file: ${bundleFile.path}")
        }
      } catch (e: SecurityException) {
        throw LeapModelLoadingException("Cannot open the bundle file: ${bundleFile.path}", e)
      }
    }

    private fun loadBundleConfigString(bundleFile: File): String? {
      return BundleProcessor.loadBundleConfigString(bundleFile.absolutePath)
    }
  }
}

private fun newConfig(generationTimeParameters: GenerationTimeParameters?): BundleConfig? =
  generationTimeParameters?.let {
    val params =
      SdkSamplerParams().apply {
        generationTimeParameters.samplingParameters?.temperature?.toFloat()?.let {
          temperature = it
        }
        generationTimeParameters.samplingParameters?.topP?.let { topP = it.toFloat() }
        generationTimeParameters.samplingParameters?.minP?.let { minP = it.toFloat() }
        generationTimeParameters.samplingParameters?.repetitionPenalty?.let {
          repetitionPenalty = it.toFloat()
        }
      }
    BundleConfig(systemMessage = null, samplerParams = params)
  }
