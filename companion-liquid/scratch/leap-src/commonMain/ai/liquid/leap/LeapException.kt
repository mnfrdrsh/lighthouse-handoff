package ai.liquid.leap

/** Base class of all exceptions thrown from Leap SDK. */
open class LeapException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause)

class LeapModelDownloadSizeMismatchException(expectedSize: Long, actualSize: Long) :
  LeapException(
    "Model download failed. Sizes do not match. Expected: $expectedSize, Actual: $actualSize"
  )

class LeapModelDownloadSha256MismatchException(expectedSha256: String, actualSha256: String) :
  LeapException(
    "Model download failed. Sha256 does not match. Expected: $expectedSha256, Actual: $actualSha256"
  )

/**
 * Failure in loading the model. It could be caused by a permission issue, file format issue, or
 * other underlying issues in the engine.
 */
class LeapModelLoadingException(message: String, cause: Throwable? = null) :
  LeapException(message, cause)

/** Error in generating contents. */
open class LeapGenerationException(message: String, cause: Throwable? = null) :
  LeapException(message, cause)

/** The specific error that the input prompt exceeds the context length. */
class LeapGenerationPromptExceedContextLengthException(message: String, cause: Throwable?) :
  LeapGenerationException(message, cause)

/** The specific error that the generated function call request cannot be correctly parsed. */
class LeapGenerationFunctionCallParsingException(message: String, cause: Throwable?) :
  LeapGenerationException(message, cause)

/** Error in serialization or deserialization. */
class LeapSerializationException(message: String, cause: Throwable? = null) :
  LeapException(message, cause)
