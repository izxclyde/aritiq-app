package com.aritiq.calcnote.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ponytail: NoteProcessor is the bridge between plain-text note bodies and the
 * calculator engine. Bugs here mis-silently total every note in the app, so it gets a
 * self-check beyond the engine's. Mirrored to iOS via the commonTest source set.
 */
class NoteProcessorTest {

    private fun liveTotal(content: String): Double = NoteProcessor.liveTotal(content)
    private fun trailing(value: String): Double? = NoteProcessor.trailingEqualsValue(value)
    private fun title(content: String): String = NoteProcessor.titleOf(content)
    private fun isTrigger(content: String): Boolean = NoteProcessor.isTotalTriggerLine(content)
    private fun triggerLabel(content: String): String? = NoteProcessor.totalTriggerLabel(content)

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            throw AssertionError("expected=$expected actual=$actual tolerance=$tolerance")
        }
    }

    // ---- liveTotal: spec flagship example ---------------------------------------------

    @Test fun live_total_shopping_list_sums_labelled_numbers() {
        val content = """
            Groceries

            Milk 12.5
            Bread 6
            Chicken 22.5

            Total =
        """.trimIndent()
        assertApprox(41.0, liveTotal(content))
    }

    @Test fun live_total_summed_assignments_before_total_marker() {
        val content = """
            Budget
            Rent = 1200
            Electricity = 150
            Internet = 90
            Total =
        """.trimIndent()
        assertApprox(1440.0, liveTotal(content))
    }

    // ---- liveTotal: label + expression parsing (the user's bug report) --------------

    @Test fun live_total_label_with_multiplication() {
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            total =
        """.trimIndent()
        // 2000 + 1000 + (1000*2) + (5000/5) = 6000
        assertApprox(6000.0, liveTotal(content))
    }

    @Test fun live_total_label_with_percent() {
        val content = """
            Laptop 3200
            Mouse 80
            Keyboard 150
        """.trimIndent()
        assertApprox(3430.0, liveTotal(content))
    }

    @Test fun live_total_label_with_unit_then_number() {
        // "Milk 2L 12.5" — strip first word fails to evaluate "2L 12.5"; fall back to suffix.
        val content = "Milk 2L 12.5"
        assertApprox(12.5, liveTotal(content))
    }

    @Test fun live_total_assignment_does_not_double_count_total_keyword() {
        // After auto-fill, the trigger line becomes "total = 6000".
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            total = 6000
        """.trimIndent()
        assertApprox(6000.0, liveTotal(content))
    }

    @Test fun live_total_skips_blank_rhs_trigger_lines() {
        val content = """
            a = 5
            b = 10
            =
        """.trimIndent()
        assertApprox(15.0, liveTotal(content))
    }

    @Test fun live_total_variable_resolution_in_assignment_rhs() {
        val content = """
            salary = 5000
            rent = 1500
            food = 700
            remaining = salary - rent - food
        """.trimIndent()
        // ponytail: budget case adds every assignment to the running sum (5000+1500+700+2800=10000).
        // We test that this is exactly the documented additive behavior — not the (Phase 2)
        // behaviour where `remaining = ...` would be treated as the headline number.
        assertApprox(10000.0, liveTotal(content))
    }

    // ---- trailingEqualsValue: triggers & evaluation ---------------------------------

    @Test fun trailing_value_for_bare_equals_is_sum_of_preceding_lines() {
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            =
        """.trimIndent()
        assertEquals(6000.0, trailing(content))
    }

    @Test fun trailing_value_for_total_equals_is_sum_of_preceding_lines() {
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            total =
        """.trimIndent()
        assertEquals(6000.0, trailing(content))
    }

    @Test fun trailing_value_for_named_trigger_still_sums() {
        val content = """
            electricity 2000
            water 1000
            xyz =
        """.trimIndent()
        assertEquals(3000.0, trailing(content))
    }

    @Test fun trailing_value_for_expression_trigger_evaluates_lhs() {
        assertEquals(105.0, trailing("100 + 5 ="))       // 100 + 5 = 105
        assertEquals(230.0, trailing("200 + 15% ="))     // spec: 200 + 15% = 230
    }

    @Test fun trailing_value_null_when_no_trigger() {
        assertNull(trailing("electricity 2000\nwater 1000"))
        assertNull(trailing(""))
        assertNull(trailing("just some text"))
    }

    @Test fun trailing_value_null_when_rhs_already_populated() {
        // After auto-fill, no re-fire — the trigger's RHS is no longer blank.
        assertNull(trailing("electricity 2000\ntotal = 2000"))
    }

    @Test fun trailing_value_null_when_rhs_filled() {
        // Live recompute is handled in the UI's onValueChange, not via NoteProcessor.
        assertNull(trailing("electricity 3000\ntotal = 2000"))
        assertNull(trailing("electricity 2000\ntotal = 2000"))
    }

    @Test fun trailing_value_live_refresh_with_multiple_lines() {
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            total = 6000
        """.trimIndent()
        // Filled RHS → null (live recompute is in the UI).
        assertNull(trailing(content))
    }

    // ---- trailingEqualsValue: bare total keyword (no `=`) --------------------------

    @Test fun trailing_value_bare_total_keyword_is_sum_of_preceding_lines() {
        val content = """
            electricity 2000
            water 1000
            grocery 1000 * 2
            sharings 5000 / 5
            total
        """.trimIndent()
        assertEquals(6000.0, trailing(content))
    }

    @Test fun trailing_value_bare_sum_keyword() {
        val content = """
            a = 100
            b = 200
            sum
        """.trimIndent()
        assertEquals(300.0, trailing(content))
    }

    @Test fun trailing_value_bare_subtotal_keyword() {
        val content = """
            Milk 12.5
            Bread 6
            subtotal
        """.trimIndent()
        assertEquals(18.5, trailing(content))
    }

    @Test fun trailing_value_bare_total_keyword_with_no_additive_lines_returns_null() {
        assertNull(trailing("total"))
        assertNull(trailing("Total"))
    }

    @Test fun trailing_value_bare_non_total_keyword_does_not_trigger() {
        assertNull(trailing("electricity 2000\nbudget"))
    }

    @Test fun trailing_value_bare_total_is_case_insensitive() {
        val content = """
            a = 100
            TOTAL
        """.trimIndent()
        assertEquals(100.0, trailing(content))
    }

    // ---- isTotalTriggerLine / totalTriggerLabel ------------------------------------

    @Test fun is_trigger_true_for_bare_total_keyword() {
        assertTrue(isTrigger("electricity 2000\ntotal"))
        assertTrue(isTrigger("a = 100\nsum"))
    }

    @Test fun is_trigger_true_for_blank_rhs_total() {
        assertTrue(isTrigger("electricity 2000\ntotal ="))
        assertTrue(isTrigger("a = 100\ngrandtotal ="))
    }

    @Test fun is_trigger_false_when_rhs_filled() {
        // User wrote their own number — must not be treated as a live trigger.
        assertFalse(isTrigger("electricity 2000\ntotal = 2000"))
    }

    @Test fun is_trigger_false_for_non_total_keyword() {
        assertFalse(isTrigger("electricity 2000\nbudget ="))
        assertFalse(isTrigger("electricity 2000\nxyz"))
        assertFalse(isTrigger("just text"))
        assertFalse(isTrigger(""))
    }

    @Test fun trigger_label_returns_keyword_for_bare_or_blank_rhs() {
        assertEquals("total", triggerLabel("electricity 2000\ntotal"))
        assertEquals("total", triggerLabel("electricity 2000\ntotal ="))
        assertEquals("sum", triggerLabel("a = 100\nsum ="))
    }

    @Test fun trigger_label_null_when_not_a_trigger() {
        assertNull(triggerLabel("electricity 2000"))
        assertNull(triggerLabel("electricity 2000\ntotal = 2000"))
    }

    // ---- title ---------------------------------------------------------------------

    @Test fun title_is_first_non_empty_line_trimmed() {
        assertEquals("Groceries", title("Groceries\nMilk 12.5"))
        assertEquals("Untitled", title("\n\n   "))
        assertEquals("Untitled", title("# \n"))
        assertEquals("My note", title("# My note\nbody"))
    }
}