package ai.liquid.leap.inferenceengine

import ai.liquid.inference_engine.GenerateOptions as JniGenerateOptions
import ai.liquid.inference_engine.JpegContent as JniJpegContent
import ai.liquid.inference_engine.Message as JniMessage
import ai.liquid.inference_engine.SamplerParams as JniSamplerParams
import ai.liquid.inference_engine.StringContent as JniStringContent
import ai.liquid.inference_engine.TokenCallback as JniTokenCallback
import ai.liquid.inference_engine.WavContent as JniWavContent

fun Message.toJni(): JniMessage {
  val jniContents =
    contents.map { content ->
      when (content) {
        is MessageContent.StringContent -> JniStringContent(content.text)
        is MessageContent.JpegContent -> JniJpegContent(content.jpegByteString.toByteArray())
        is MessageContent.WavContent -> JniWavContent(content.wavByteString.toByteArray())
      }
    }
  return JniMessage(role, jniContents)
}

fun GenerateOptions.toJni(): JniGenerateOptions {
  val jniCallback =
    callback?.let { cb -> JniTokenCallback { token, isSpecial -> cb.onToken(token, isSpecial) } }
      ?: JniTokenCallback { _, _ -> }
  return JniGenerateOptions(tokenCallback = jniCallback, samplerParams = samplerParams?.toJni())
}

fun SamplerParams.toJni(): JniSamplerParams =
  JniSamplerParams(
    temperature = temperature,
    topP = topP,
    minP = minP,
    repetitionPenalty = repetitionPenalty,
    topK = topK,
    rngSeed = randomSeed,
  )

fun GenerationStopReason.Companion.fromJni(jni: Any?): GenerationStopReason =
  when (jni?.toString()) {
    "FINISHED" -> GenerationStopReason.FINISHED
    "INTERRUPTED" -> GenerationStopReason.INTERRUPTED
    "OUT_OF_CONTEXT" -> GenerationStopReason.OUT_OF_CONTEXT
    else -> GenerationStopReason.UNKNOWN
  }
