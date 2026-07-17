package com.aritiq.calcnote.calculator

/**
 * Public façade. Pure Kotlin, no UI dependency. State unsafe-threadsafe: create per-call or reuse.
 *
 * Usage:
 *   Calculator.evaluate("12 + 5")                        // Ok(17.0)
 *   Calculator.evaluate("200 + 15%", variables=...)       // Ok(230.0)  per spec
 *   Calculator.evaluate("salary - rent - food", vars=...) // Ok(2800.0)
 *
 * Variables are case-insensitive keys.
 */
object Calculator {

    /** Inline variable scope: case-insensitive keys. Pass emptyMap() for empty scope. */
    fun evaluate(
        expression: String,
        variables: Map<String, Double> = emptyMap(),
    ): Result<Double> {
        if (expression.isBlank()) return Result.success(0.0)
        val lower = variables.entries.associate { it.key.lowercase() to it.value }
        return runCatching {
            val tokens = Tokenizer(expression).tokenize()
            val node = Parser(tokens).parse()
            Evaluator(lower).eval(node)
        }.recoverCatching { e ->
            throw if (e is CalculatorError) e else CalculatorError.Syntax(e.message ?: "invalid expression")
        }
    }
}