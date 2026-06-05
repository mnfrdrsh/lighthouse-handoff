package ai.liquid.leap.structuredoutput

import ai.liquid.leap.LeapException

/** Exception in creating the JSONSchema for a generatable data class. */
class LeapGeneratableSchematizationException(message: String, cause: Throwable? = null) :
  LeapException(message, cause)

/** Exception in creating generatable objects from JSON. */
class LeapGeneratableDeserializationException(message: String, cause: Throwable? = null) :
  LeapException(message, cause)
