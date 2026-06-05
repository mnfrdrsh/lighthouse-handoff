package ai.liquid.leap.function.pythonic

/** Name/type of the token. */
enum class PythonicTokenName {
  LEFT_SQUARE_BRACKET, // [
  RIGHT_SQUARE_BRACKET, // ]
  LEFT_PARENTHESES, // (
  RIGHT_PARENTHESES, // )
  LEFT_CURLY_BRACKET, // {
  RIGHT_CURLY_BRACKET, // }
  COLON, // :
  COMMA, // ,
  EQUAL, // =
  IDENTIFIER, // names of functions or parameters and other keywords like an identifier,
  NUMBER_LITERAL, // any numbers
  STRING_LITERAL, // any string literal
  SPACE, // any space
}

/** Data class of a recognized token. */
data class PythonicToken(val name: PythonicTokenName, val text: String)
