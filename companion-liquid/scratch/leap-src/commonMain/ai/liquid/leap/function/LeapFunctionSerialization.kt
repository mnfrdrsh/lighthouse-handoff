package ai.liquid.leap.function

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of [LeapFunctionParameterType]. Used for AIDL wire transport
 * (leap-model-service) where the native SDK type hierarchy can't cross process boundaries directly.
 * Convert via [LeapFunctionParameterType.toDto] and [LeapFunctionParameterTypeDto.toDomain].
 *
 * The subclass tags (`str`, `num`, etc.) are the wire format — keep them stable.
 */
@Serializable
public sealed class LeapFunctionParameterTypeDto {
  public abstract val description: String?

  @Serializable
  @SerialName("str")
  public data class Str(
    override val description: String? = null,
    val enumValues: List<String>? = null,
  ) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("num")
  public data class Num(
    override val description: String? = null,
    // Lossy for Float/Long — AIDL wire is JSON, so all numeric enum values
    // round-trip as Double. Callers using typed Number enums should prefer
    // LeapInt for integers.
    val enumValues: List<Double>? = null,
  ) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("int")
  public data class IntType(
    override val description: String? = null,
    val enumValues: List<Int>? = null,
  ) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("bool")
  public data class Bool(override val description: String? = null) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("arr")
  public data class Arr(
    override val description: String? = null,
    val itemType: LeapFunctionParameterTypeDto,
  ) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("obj")
  public data class Obj(
    override val description: String? = null,
    val properties: Map<String, LeapFunctionParameterTypeDto>,
    val required: List<String> = emptyList(),
  ) : LeapFunctionParameterTypeDto()

  @Serializable
  @SerialName("null")
  public data class NullType(override val description: String? = null) :
    LeapFunctionParameterTypeDto()
}

/** Serializable mirror of [LeapFunctionParameter]. */
@Serializable
public data class LeapFunctionParameterDto(
  val name: String,
  val type: LeapFunctionParameterTypeDto,
  val description: String,
  val optional: Boolean = false,
)

/**
 * Serializable mirror of [LeapFunction]. Used by the leap-model-service AIDL layer so client and
 * service can exchange function schemas without the [LeapFunction] sealed hierarchy being part of
 * the AIDL wire protocol directly.
 */
@Serializable
public data class LeapFunctionDto(
  val name: String,
  val description: String,
  val parameters: List<LeapFunctionParameterDto>,
)

// --- Conversions ---

public fun LeapFunctionParameterType.toDto(): LeapFunctionParameterTypeDto =
  when (this) {
    is LeapFunctionParameterType.LeapStr ->
      LeapFunctionParameterTypeDto.Str(description, enumValues)
    is LeapFunctionParameterType.LeapNum ->
      LeapFunctionParameterTypeDto.Num(description, enumValues?.map { it.toDouble() })
    is LeapFunctionParameterType.LeapInt ->
      LeapFunctionParameterTypeDto.IntType(description, enumValues)
    is LeapFunctionParameterType.LeapBool -> LeapFunctionParameterTypeDto.Bool(description)
    is LeapFunctionParameterType.LeapArr ->
      LeapFunctionParameterTypeDto.Arr(description, itemType.toDto())
    is LeapFunctionParameterType.LeapObj ->
      LeapFunctionParameterTypeDto.Obj(
        description = description,
        properties = properties.mapValues { (_, v) -> v.toDto() },
        required = required,
      )
    is LeapFunctionParameterType.LeapNull -> LeapFunctionParameterTypeDto.NullType(description)
  }

public fun LeapFunctionParameterTypeDto.toDomain(): LeapFunctionParameterType =
  when (this) {
    is LeapFunctionParameterTypeDto.Str ->
      LeapFunctionParameterType.LeapStr(enumValues = enumValues, description = description)
    is LeapFunctionParameterTypeDto.Num ->
      LeapFunctionParameterType.LeapNum(enumValues = enumValues, description = description)
    is LeapFunctionParameterTypeDto.IntType ->
      LeapFunctionParameterType.LeapInt(enumValues = enumValues, description = description)
    is LeapFunctionParameterTypeDto.Bool ->
      LeapFunctionParameterType.LeapBool(description = description)
    is LeapFunctionParameterTypeDto.Arr ->
      LeapFunctionParameterType.LeapArr(itemType = itemType.toDomain(), description = description)
    is LeapFunctionParameterTypeDto.Obj ->
      LeapFunctionParameterType.LeapObj(
        properties = properties.mapValues { (_, v) -> v.toDomain() },
        required = required,
        description = description,
      )
    is LeapFunctionParameterTypeDto.NullType -> LeapFunctionParameterType.LeapNull()
  }

public fun LeapFunctionParameter.toDto(): LeapFunctionParameterDto =
  LeapFunctionParameterDto(
    name = name,
    type = type.toDto(),
    description = description,
    optional = optional,
  )

public fun LeapFunctionParameterDto.toDomain(): LeapFunctionParameter =
  LeapFunctionParameter(
    name = name,
    type = type.toDomain(),
    description = description,
    optional = optional,
  )

public fun LeapFunction.toDto(): LeapFunctionDto =
  LeapFunctionDto(
    name = name,
    description = description,
    parameters = parameters.map { it.toDto() },
  )

public fun LeapFunctionDto.toDomain(): LeapFunction =
  LeapFunction(
    name = name,
    description = description,
    parameters = parameters.map { it.toDomain() },
  )
