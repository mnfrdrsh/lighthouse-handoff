package ai.liquid.leap.function.pythonic

import ai.liquid.leap.function.LeapFunctionCall

object PythonicDumper {
  /**
   * Serialize the function calls into Pythnoic format. The input should be created by
   * [PythonicParser]. Otherwise, the return value could be invalid.
   */
  fun dumpFunctionCalls(calls: List<LeapFunctionCall>): String {
    val callsString = calls.joinToString { dumpFunctionCall(it) }
    return "[$callsString]"
  }

  private fun dumpFunctionCall(call: LeapFunctionCall): String {
    val functionName = call.name
    val argumentString =
      call.arguments.entries
        .sortedBy { it.key }
        .joinToString { (key, value) ->
          val valueString = dumpValue(value)
          "$key=$valueString"
        }
    return "$functionName($argumentString)"
  }

  private fun dumpValue(value: Any?): String =
    when (value) {
      null -> {
        "None"
      }

      is List<*> -> {
        "[${value.joinToString { dumpValue(it) }}]"
      }

      is Map<*, *> -> {
        val items =
          value.entries
            .sortedBy { it.key.toString() }
            .joinToString { (key, value) -> "${dumpValue(key)}:${dumpValue(value)}" }
        "{$items}"
      }

      is Boolean -> {
        if (value) {
          "True"
        } else {
          "False"
        }
      }

      is String -> {
        "\"$value\""
      }

      else -> {
        value.toString()
      }
    }
}
