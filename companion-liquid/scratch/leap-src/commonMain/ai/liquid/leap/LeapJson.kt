package ai.liquid.leap

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for Leap SDK serialization.
 *
 * This instance is configured to:
 * - Ignore unknown fields in JSON (for forward compatibility)
 * - Not encode default/null values (reduce payload size)
 * - Coerce invalid values to defaults (defensive parsing)
 * - Not write explicit nulls in JSON output
 */
val LeapJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = false
  coerceInputValues = true
  explicitNulls = false
}

/**
 * JSON configuration with pretty printing enabled for human-readable output.
 *
 * Used for debugging, schema generation, and formatted JSON output.
 */
val LeapJsonPretty = Json {
  prettyPrint = true
  ignoreUnknownKeys = true
  encodeDefaults = false
  coerceInputValues = true
  explicitNulls = false
}
