package ai.liquid.leap.structuredoutput

import ai.liquid.leap.LeapJson
import ai.liquid.leap.LeapJsonPretty
import ai.liquid.leap.LeapThrows
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

object JSONSchemaGenerator {
  // Cache per descriptor serialName to avoid rebuilding schemas repeatedly
  private val jsonSchemaCache = hashMapOf<String, JsonObject>()

  /**
   * Generate a JSON Schema string from a data class serializer. This JSON Schema string can be used
   * in both the prompt and the constraint.
   *
   * @param serializer [KSerializer] of the data class for creating the JSONSchema.
   * @param indentSpaces intent spaces for adding indent in the generated string. `null` value will
   *   create outputs without indents
   */
  @LeapThrows(LeapGeneratableSchematizationException::class)
  @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
  fun <T : Any> getJSONSchema(serializer: KSerializer<T>, indentSpaces: Int? = null): String {
    val obj = getJSONSchemaObject(serializer.descriptor, mutableSetOf())
    return if (indentSpaces != null) {
      LeapJsonPretty.encodeToString(JsonObject.serializer(), obj)
    } else {
      LeapJson.encodeToString(JsonObject.serializer(), obj)
    }
  }

  /**
   * Generate a JSON Schema string from a data class [T]. This JSON Schema string can be used in
   * both the prompt and the constraint.
   *
   * @param indentSpaces intent spaces for adding indent in the generated string. `null` value will
   *   create outputs without indents
   */
  @LeapThrows(LeapGeneratableSchematizationException::class)
  inline fun <reified T : Any> getJSONSchema(indentSpaces: Int? = null): String {
    val serializer =
      try {
        serializer<T>()
      } catch (t: Throwable) {
        throw LeapGeneratableSchematizationException(
          "Type must be @Serializable to generate JSON Schema",
          t,
        )
      }
    return getJSONSchema(serializer, indentSpaces)
  }

  private fun getJSONSchemaObject(
    descriptor: SerialDescriptor,
    stack: MutableSet<String>,
  ): JsonObject {
    val serialName = descriptor.serialName
    if (serialName in stack) {
      throw LeapGeneratableSchematizationException(
        "Recursive data type $serialName is not supported."
      )
    }
    jsonSchemaCache[serialName]?.let {
      return it
    }
    stack.add(serialName)

    val generatable = descriptor.annotations.filterIsInstance<Generatable>().firstOrNull()

    if (generatable == null) {
      // For KSP generated serializers, descriptors might have different characteristics.
      // If we got here from a contextual serializer, we should trust it if it has the right serial
      // name.
      if (
        descriptor.elementsCount >= 0 ||
          descriptor.kind == SerialKind.ENUM ||
          descriptor.kind.toString().contains("ENUM") ||
          descriptor.kind == StructureKind.OBJECT
      ) {
        // Likely a KSP generated descriptor where annotations might be missing in some contexts
        // or we just didn't find them. Let's try to proceed.
      } else {
        throw LeapGeneratableSchematizationException(
          "$serialName should be annotated with Generatable"
        )
      }
    }

    val propertiesSchemaMap = linkedMapOf<String, JsonObject>()
    val requiredPropertiesList = mutableListOf<String>()

    if (
      descriptor.kind != StructureKind.CLASS &&
        descriptor.kind != SerialKind.ENUM &&
        descriptor.kind != StructureKind.OBJECT &&
        !descriptor.kind.toString().endsWith("ENUM")
    ) {
      throw LeapGeneratableSchematizationException(
        "JSONSchema can only be created for class, enum or object descriptors annotated with Generatable"
      )
    }

    if (descriptor.kind == SerialKind.ENUM || descriptor.kind.toString().contains("ENUM")) {
      val enumObj = enumSchema(descriptor)
      jsonSchemaCache[serialName] = enumObj
      stack.remove(serialName)
      return enumObj
    }

    for (i in 0 until descriptor.elementsCount) {
      val name = descriptor.getElementName(i)
      val elementDesc = descriptor.getElementDescriptor(i)
      var schemaObject = getJSONSchemaObjectOfType(elementDesc, stack)

      val guide = descriptor.getElementAnnotations(i).filterIsInstance<Guide>().firstOrNull()
      if (guide != null) {
        schemaObject = buildJsonObject {
          schemaObject.forEach { (k, v) -> put(k, v) }
          put("description", guide.description)
        }
      }
      if (!descriptor.isElementOptional(i)) {
        requiredPropertiesList.add(name)
      }
      propertiesSchemaMap[name] = schemaObject
    }

    val title = serialName.substringAfterLast('.')
    val schemaObject = buildJsonObject {
      put("title", title)
      generatable?.description?.let { put("description", it) }
      // Default description for KSP-generated classes if missing
      if (generatable == null && descriptor.kind != SerialKind.ENUM) {
        put("description", "Generated schema for $title")
      }
      put("type", "object")
      put("properties", buildJsonObject { propertiesSchemaMap.forEach { (k, v) -> put(k, v) } })
      put("required", buildJsonArray { requiredPropertiesList.forEach { add(it) } })
    }
    jsonSchemaCache[serialName] = schemaObject
    stack.remove(serialName)
    return schemaObject
  }

  private fun primitiveSchema(kind: PrimitiveKind): JsonObject =
    when (kind) {
      PrimitiveKind.STRING -> buildJsonObject { put("type", "string") }
      PrimitiveKind.INT,
      PrimitiveKind.LONG -> buildJsonObject { put("type", "integer") }
      PrimitiveKind.FLOAT,
      PrimitiveKind.DOUBLE -> buildJsonObject { put("type", "number") }
      PrimitiveKind.BOOLEAN -> buildJsonObject { put("type", "boolean") }
      PrimitiveKind.BYTE,
      PrimitiveKind.SHORT,
      PrimitiveKind.CHAR -> buildJsonObject { put("type", "integer") }
    }

  private fun arraySchema(element: SerialDescriptor, stack: MutableSet<String>): JsonObject =
    buildJsonObject {
      put("type", "array")
      put("items", getJSONSchemaObjectOfType(element, stack))
    }

  private fun enumSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {
    put(
      "enum",
      buildJsonArray {
        for (i in 0 until descriptor.elementsCount) {
          add(descriptor.getElementName(i))
        }
      },
    )
  }

  private fun getJSONSchemaObjectOfType(
    descriptor: SerialDescriptor,
    stack: MutableSet<String>,
  ): JsonObject {
    if (descriptor.kind == SerialKind.ENUM || descriptor.kind.toString().contains("ENUM")) {
      return enumSchema(descriptor)
    }
    return when (descriptor.kind) {
      is PrimitiveKind -> primitiveSchema(descriptor.kind as PrimitiveKind)
      StructureKind.LIST -> arraySchema(descriptor.getElementDescriptor(0), stack)
      StructureKind.CLASS,
      StructureKind.OBJECT -> getJSONSchemaObject(descriptor, stack)
      StructureKind.MAP ->
        throw LeapGeneratableSchematizationException("Unsupported data type: MAP")
      else ->
        throw LeapGeneratableSchematizationException("Unsupported data type: ${descriptor.kind}")
    }
  }
}
