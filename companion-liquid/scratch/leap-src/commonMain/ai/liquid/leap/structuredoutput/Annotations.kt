@file:OptIn(ExperimentalSerializationApi::class)

package ai.liquid.leap.structuredoutput

import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Allowing a data class to be generatable. This unblocks [JSONSchemaGenerator.getJSONSchema] to
 * create JSON Schema for the data class.
 */
@kotlinx.serialization.SerialInfo
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Generatable(val description: String)

/**
 * Add description for a field of the data class annotated with [Generatable]. The description will
 * be attached in the generated JSON Schema for guiding the generation.
 */
@kotlinx.serialization.SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Guide(val description: String)
