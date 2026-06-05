package ai.liquid.leap.inferenceengine

class Constraints private constructor(val json: String) {
  companion object {
    fun fromJson(json: String): Constraints = Constraints(json)
  }
}
