package ai.liquid.leap.function

import ai.liquid.leap.function.hermes.HermesDumper
import ai.liquid.leap.function.hermes.HermesParser
import ai.liquid.leap.function.pythonic.PythonicDumper
import ai.liquid.leap.function.pythonic.PythonicParser

/**
 * Abstract class to describe a function call parser in Leap.
 *
 * @param toolCallStartToken The token indicates the start of the function call request content
 * @param toolCallEndToken The token indicates the end of the function call request content
 */
abstract class LeapFunctionCallParser(
  val toolCallStartToken: String,
  val toolCallEndToken: String,
) {
  protected var buffer = StringBuilder()

  /** Append a chunk into the buffer of the parser. */
  fun append(chunk: String) {
    buffer.append(chunk)
  }

  /** Clear the buffer of the parser. */
  fun clear() {
    buffer = StringBuilder()
  }

  /** Parse the content in the buffer and clear the buffer. */
  abstract fun parse(): List<LeapFunctionCall>

  abstract fun dump(functionCalls: List<LeapFunctionCall>): String
}

/** Function call parsers for Liquid Foundation Models (LFM2). */
class LFMFunctionCallParser :
  LeapFunctionCallParser(
    toolCallStartToken = "<|tool_call_start|>",
    toolCallEndToken = "<|tool_call_end|>",
  ) {
  override fun parse(): List<LeapFunctionCall> {
    val parser = PythonicParser(buffer.toString())
    buffer = StringBuilder()
    return parser.parsePythonicCalls()
  }

  override fun dump(functionCalls: List<LeapFunctionCall>): String =
    "$toolCallStartToken${PythonicDumper.dumpFunctionCalls(functionCalls)}$toolCallEndToken"
}

/**
 * Function call parsers for models that are using
 * [Hermes function calling format](https://github.com/NousResearch/Hermes-Function-Calling). For
 * example, Qwen3 models.
 */
class HermesFunctionCallParser :
  LeapFunctionCallParser(toolCallStartToken = "<tool_call>", toolCallEndToken = "</tool_call>") {
  override fun parse(): List<LeapFunctionCall> {
    val function = HermesParser.parse(buffer.toString())
    buffer = StringBuilder()
    return listOf(function)
  }

  override fun dump(functionCalls: List<LeapFunctionCall>): String =
    functionCalls.joinToString(separator = "") { call ->
      "$toolCallStartToken${HermesDumper.dump(call)}$toolCallEndToken"
    }
}
