package ai.liquid.leap.function.pythonic

/** Errors on syntax in Pythonic function call codes, emitted by the parser */
class PythonicSyntaxError(val pos: Int, val reason: String) :
  RuntimeException("Syntax error at $pos: $reason")
