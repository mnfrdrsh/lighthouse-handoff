package ai.liquid.leap.function

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Describe the signature of a function that can be called by the model.
 *
 * @param name name of the function
 * @param description human-readable / LLM-readable description of the function
 * @param parameters list of parameters that accepted by the function
 */
data class LeapFunction(
  val name: String,
  @OptIn(ExperimentalObjCName::class)
  @param:ObjCName("functionDescription")
  val description: String,
  val parameters: List<LeapFunctionParameter>,
) {
  /**
   * Create a JsonObject representation of this function.
   *
   * Args: `withToolTypeWrapper`: whether to wrap the function with a type declaration for common
   * tool calling APIs
   */
  fun toJsonObject(withToolTypeWrapper: Boolean = true): JsonObject {
    // Create the JSON Schema object for the parameters
    val requiredParameters = mutableListOf<String>()
    val parametersMap = mutableMapOf<String, LeapFunctionParameterType>()
    for (parameter in parameters) {
      if (!parameter.optional) {
        requiredParameters.add(parameter.name)
      }
      parametersMap.put(parameter.name, parameter.type.attachDescription(parameter.description))
    }
    val parametersObject =
      LeapFunctionParameterType.LeapObj(properties = parametersMap, required = requiredParameters)

    val obj = buildJsonObject {
      put("name", name)
      put("description", description)
      put("parameters", parametersObject.toJsonObject())
    }

    return if (!withToolTypeWrapper) {
      obj
    } else {
      buildJsonObject {
        put("type", "function")
        put("function", obj)
      }
    }
  }
}

/**
 * Describe the signature of a parameter that can be called by the model.
 *
 * @param name name of the parameter
 * @param type data type of the parameter
 * @param description human-readable / LLM-readable description of the function
 * @param optional whether the parameter is optional
 */
data class LeapFunctionParameter(
  val name: String,
  val type: LeapFunctionParameterType,
  @OptIn(ExperimentalObjCName::class)
  @param:ObjCName("parameterDescription")
  val description: String,
  val optional: Boolean = false,
)
