package com.aritiq.calcnote.calculator

import kotlin.math.*

/**
 * Evaluates an [Node] AST with the provided read-only variable scope.
 * Mutations: nothing. Exceptions: [CalculatorError] subclasses only.
 *
 * @param variables Case-insensitive variable lookup (so `salary` and `SALARY` refer
 *   to the same binding — CalcNote is for humans, not compilers).
 */
internal class Evaluator(private val variables: Map<String, Double>) {
    fun eval(node: Node): Double = when (node) {
        is Node.Num -> node.value
        is Node.Var -> variables[node.name.lowercase()]
            ?: constants[node.name.lowercase()]
            ?: throw CalculatorError.UnknownVariable(node.name)
        is Node.Unary -> when (node.op) {
            '-' -> -eval(node.operand)
            else -> eval(node.operand)
        }
        is Node.Binary -> {
            val l = eval(node.left)
            val r = eval(node.right)
            binary(node.op, l, r)
        }
        is Node.Percent -> eval(node.operand) / 100.0
        is Node.PercentOf -> percentOf(node.op, eval(node.base), eval(node.pct))
        is Node.Call -> {
            val args = node.args.map { eval(it) }
            when (node.name) {
                "sqrt" -> one(node.name, args) { sqrt(it) }
                "abs" -> one(node.name, args) { abs(it) }
                "round" -> one(node.name, args) { round(it) }
                "floor" -> one(node.name, args) { floor(it) }
                "ceil" -> one(node.name, args) { ceil(it) }
                "sin" -> one(node.name, args) { sin(it) }
                "cos" -> one(node.name, args) { cos(it) }
                "tan" -> one(node.name, args) { tan(it) }
                "log" -> one(node.name, args) { log10(it) }
                "ln" -> one(node.name, args) { ln(it) }
                "max" -> twoOrMore(node.name, args) { a, b -> max(a, b) }
                "min" -> twoOrMore(node.name, args) { a, b -> min(a, b) }
                else -> throw CalculatorError.UnknownFunction(node.name)
            }
        }
    }

    private fun binary(op: Char, l: Double, r: Double): Double = when (op) {
        '+' -> l + r
        '-' -> l - r
        '*' -> l * r
        '/' -> if (r == 0.0) throw CalculatorError.Division() else l / r
        '^' -> l.pow(r)
        else -> throw CalculatorError.Syntax("Unknown operator '$op'")
    }

    private fun one(fn: String, args: List<Double>, f: (Double) -> Double): Double {
        if (args.size != 1) throw CalculatorError.Arity(fn, 1, args.size)
        return f(args[0])
    }
    private fun twoOrMore(fn: String, args: List<Double>, f: (Double, Double) -> Double): Double {
        if (args.size < 2) throw CalculatorError.Arity(fn, 2, args.size)
        return args.reduce(f)
    }

    /**
     * Spec-compliant percent semantics. `+`/`-`: percent-of-base. `*`/`/`: percent as scalar.
     * 200 + 15% = 200 + 30  = 230.  500 - 10% = 500 - 50   = 450.
     * 1000 * 5% = 1000 * 0.05 = 50.  200 / 5% = 200 / 0.05 = 4000.
     */
    private fun percentOf(op: Char, base: Double, pct: Double): Double = when (op) {
        '+' -> base + base * pct / 100.0
        '-' -> base - base * pct / 100.0
        '*' -> base * (pct / 100.0)
        '/' -> if (pct == 0.0) throw CalculatorError.Division() else base / (pct / 100.0)
        else -> throw CalculatorError.Syntax("Operator '$op' cannot take a percent modifier")
    }

    companion object {
        private val constants = mapOf(
            "pi" to PI,
            "e" to E,
        )
    }
}