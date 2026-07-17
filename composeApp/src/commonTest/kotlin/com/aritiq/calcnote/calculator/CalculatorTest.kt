package com.aritiq.calcnote.calculator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Ponytail: one runnable check file per the engine's self-check contract. This one is more
 * thorough because money/correctness lives here — bugs in the engine hurt every note that
 * uses `=`. Exposed as commonTest so the iOS source set gets these same checks for free
 * when the iOS target comes online.
 */
class CalculatorTest {

    private fun eval(expr: String, vars: Map<String, Double> = emptyMap()): Double =
        Calculator.evaluate(expr, vars).getOrThrow()

    @Test fun basic_arithmetic() {
        assertApprox(17.0, eval("12+5"))
        assertApprox(17.0, eval("12 + 5"))
        assertApprox(72.0, eval("18*4"))
        assertApprox(33.3333333333, eval("100/3"), 1e-6)
        assertApprox(130.0, eval("150-20"))
        assertApprox(0.0, eval(""))
        assertApprox(0.0, eval("   "))
    }

    @Test fun precedence() {
        assertApprox(51.0, eval("(12+5)*3"))
        assertApprox(27.0, eval("12+5*3"))
        assertApprox(125.0, eval("5^3"))
        assertApprox(11.0, eval("2+3^2"))          // ^ beats + per math convention
        assertApprox(8.0, eval("2^3^1"))           // right-assoc ^ -> 2^(3^1) = 2^3 = 8
    }

    @Test fun unary_minus_and_nested() {
        assertApprox(-5.0, eval("-5"))
        assertApprox(10.0, eval("5--5"))            // 5 - (-5) = 10
        assertApprox(-10.0, eval("-(2*5)"))
        assertApprox(-3.0, eval("2+-5"))            // 2 + (-5) = -3
    }

    @Test fun thousands_separator_recognition() {
        assertApprox(1200.55, eval("1,200.55"))
        assertApprox(1200.55, eval("1200.55"))
        assertApprox(1_000_000.0, eval("1,000,000"))
        assertApprox(25_000.0, eval("25,000"))     // 2L + 3R, canonical
    }

    @Test fun thousands_sep_does_not_eat_function_arg_commas() {
        // critical: `2,3` inside a func call MUST stay two args. Tight sep rule rejects RHS lengths != 3.
        assertApprox(3.0, eval("max(2,3)"))
        assertApprox(2.0, eval("min(2,3)"))
        assertApprox(9.0, eval("max(1,9,2)"))
    }

    @Test fun bare_percent() {
        assertApprox(0.25, eval("25%"))
        assertApprox(0.5, eval("50%"))
        assertApprox(0.025, eval("2.5%"))
        assertApprox(0.25, eval("(25)%"))           // percent on a parenthesized atom
    }

    @Test fun percent_addition() {
        assertApprox(230.0, eval("200 + 15%"))      // spec: 200 + 15% = 230
        assertApprox(230.0, eval("200+15%"))
    }

    @Test fun percent_subtraction() {
        assertApprox(450.0, eval("500 - 10%"))      // spec: 500 - 10% = 450
    }

    @Test fun percent_multiplication() {
        assertApprox(50.0, eval("1000 * 5%"))       // spec: 1000*5% = 50
        assertApprox(50.0, eval("1000*5%"))
    }

    @Test fun percent_division() {
        assertApprox(4000.0, eval("200 / 5%"))       // consistent with the multiplication form
    }

    @Test fun chained_percent() {
        // 25% + 5: bare percent binds 0.25, then 5 -> 5.25. Trailing operand has no %.
        assertApprox(5.25, eval("25% + 5"))
        // 100 + 5% + 10: PercentOf(+, 100, 5) = 105, then + 10 = 115
        assertApprox(115.0, eval("100 + 5% + 10"))
    }

    @Test fun variables_case_insensitive() {
        val vars = mapOf("salary" to 5000.0, "RENT" to 1500.0, "food" to 700.0)
        assertApprox(2800.0, eval("salary - rent - food", vars))
        assertApprox(2800.0, eval("SALARY - rent - Food", vars))
    }

    @Test fun variables_in_arithmetic() {
        val vars = mapOf("remaining" to 2800.0)
        assertApprox(2800.0, eval("remaining", vars))
        assertApprox(5600.0, eval("remaining*2", vars))
        assertApprox(2800.0, eval("(remaining)", vars))
    }

    @Test fun functions() {
        assertApprox(4.0, eval("sqrt(16)"))
        assertApprox(5.0, eval("abs(-5)"))
        assertApprox(3.0, eval("round(2.6)"))       // round-half-to-even, so 2.5 -> 2; pick 2.6 to test the half-free case
        assertApprox(9.0, eval("log(1000000000)"))    // log = log10; log10(1e9) = 9
        assertApprox(9.0, eval("max(1,9,2)"))
        assertApprox(1.0, eval("min(1,9,2)"))
        assertApprox(3.0, eval("floor(3.7)"))
        assertApprox(2.0, eval("max(2,3) - 1"))
    }

    @Test fun constants() {
        assertApprox(kotlin.math.PI, eval("pi"), 1e-9)
        assertApprox(kotlin.math.E, eval("e"), 1e-9)
    }

    @Test fun error_on_unknown_variable() {
        val ex = assertFailsWith<CalculatorError.UnknownVariable> {
            eval("x + 1")
        }
        assertTrue(ex.name == "x")
    }

    @Test fun error_on_unknown_function() {
        assertFailsWith<CalculatorError.UnknownFunction> {
            eval("frob(1)")
        }
    }

    @Test fun error_on_division_by_zero() {
        assertFailsWith<CalculatorError.Division> {
            eval("1 / 0")
        }
    }

    @Test fun error_on_unbalanced_parens() {
        assertFailsWith<CalculatorError.Syntax> {
            eval("(1+2")
        }
    }

    @Test fun error_on_trailing_operator() {
        assertFailsWith<CalculatorError.Syntax> {
            eval("1+")
        }
    }

    @Test fun result_wrapper_returns_failure() {
        val r = Calculator.evaluate("foo")
        assertTrue(r.isFailure)
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            throw AssertionError("expected=$expected actual=$actual tolerance=$tolerance")
        }
    }
}