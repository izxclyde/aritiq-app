package com.aritiq.calcnote.calculator

/** Errors raised anywhere in the engine. UI catches Result<Nothing>. */
sealed class CalculatorError(message: String) : RuntimeException(message) {
    class Syntax(message: String) : CalculatorError(message)
    class UnknownVariable(val name: String) : CalculatorError("Unknown variable '$name'")
    class UnknownFunction(val name: String) : CalculatorError("Unknown function '$name'")
    class Arity(val fn: String, expected: Int, actual: Int) :
        CalculatorError("'$fn' expects $expected argument(s), got $actual")
    class Division : CalculatorError("Division by zero")
}