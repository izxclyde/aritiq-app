package com.aritiq.calcnote.domain

import com.aritiq.calcnote.calculator.Calculator

/**
 * Converts a raw note body into a title and computes live/auto totals. Pure Kotlin, shared
 * across platforms. Two output channels:
 *
 *   1. [liveTotal]      - the "running sum" shown in the editor's bottom status bar.
 *   2. [trailingEqualsValue] - the value to auto-fill when the user types a `Name =` or
 *      `expr =` trigger line at the end of the note.
 *
 * ## Per-line parsing model
 *
 * A line is one of:
 *   - blank / free text           -> contributes nothing
 *   - `=` or `Name =` (blank RHS) -> trigger marker, contributes nothing (its value is filled
 *                                    by [trailingEqualsValue] and inserted by the editor)
 *   - `Name = expr`               -> assignment. Stores `Name` in scope for later lines. If
 *                                    `Name` is a total keyword, it does NOT add to the running
 *                                    sum (it labels a closing total). Otherwise it adds.
 *   - whole-line expression        -> `200 + 15%`, `sqrt(16)`. Evaluates and adds.
 *   - `label <expr>`              -> "grocery 1000 * 2" — strip the first word, evaluate the
 *                                    remainder. Picks up `1000 * 2 = 2000` and `5000 / 5 = 1000`.
 *   - `label <number>`            -> "milk 12.5" — evaluates the suffix after the last space
 *                                    (also handles "Milk 2L 12.5" via suffix fallback).
 *   - bare number                  -> adds itself.
 *
 * ## Total keywords
 *
 * Lines whose LHS is one of [totalKeywords] ("total", "sum", "subtotal", "grand total",
 * "grandtotal", "Σ", "σύνολο") are treated as closing markers, not additive. The bottom
 * bar shows the sum of all additive lines before the marker.
 *
 * ponytail: live re-evaluation when a number above an already-filled `total = ...` line
 * changes is Phase 2. For now the user re-types `=` after the total keyword to refresh.
 */
object NoteProcessor {

    private val identRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Keywords that label a closing total line (not an additive entry). */
    val totalKeywords = setOf(
        "total", "sum", "subtotal", "grand", "grandtotal", "σ", "σύνολο",
    )

    /** Derives a title from content (first non-empty line, trimmed). Blank notes -> "Untitled". */
    fun titleOf(content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return "Untitled"
        return firstLine.trim().trimStart('#').trimStart('*').trim().ifBlank { "Untitled" }
    }

    /**
     * If the note's last non-empty line is a trigger (`=`, `Name =`, or `expr =` with a
     * blank RHS), returns the value to fill in. Also returns a new value when the last
     * line is a total-keyword assignment (`Name = <value>`) and the sum of preceding
     * lines differs from the current RHS (live refresh). Returns null otherwise.
     *
     *  - `=` or `Name =` where Name is empty or a total keyword identifier -> SUM of all
     *    preceding additive lines.
     *  - `Name =` where Name is a non-total identifier -> same SUM (treated as a label for
     *    the running total; the var is set to the sum).
     *  - `expr =` where the LHS is a real expression (e.g. `100 + 5 =`) -> EVALUATE the LHS
     *    standalone.
     *  - `Name = <value>` where Name is a total keyword and RHS is non-blank -> recompute
     *    sum of preceding lines; if different from current RHS, return the new sum (triggers
     *    live refresh in the editor).
     */
    fun trailingEqualsValue(content: String): Double? {
        val lines = content.split('\n')
        val lastIdx = lines.indexOfLast { it.isNotBlank() }
        if (lastIdx < 0) return null
        val lastTrim = lines[lastIdx].trim()
        // Bare total keyword (no `=`): "total", "sum", etc. → sum preceding lines.
        if (!lastTrim.contains("=")) {
            if (lastTrim.lowercase() in totalKeywords) {
                val vars = LinkedHashMap<String, Double>()
                var sum = 0.0
                var any = false
                for (i in 0 until lastIdx) {
                    val v = lineValue(lines[i], vars)
                    if (v != null) { sum += v; any = true }
                }
                return if (any) sum else null
            }
            return null
        }
        val eq = lastTrim.indexOf('=')
        if (eq < 0) return null
        val lhs = lastTrim.substring(0, eq).trim()
        val rhs = lastTrim.substring(eq + 1).trim()

        // Case 1: blank RHS → sum preceding lines (or evaluate LHS if it's an expression).
        if (rhs.isEmpty()) {
            if (lhs.isEmpty() || lhs.matches(identRegex)) {
                val vars = LinkedHashMap<String, Double>()
                var sum = 0.0
                var any = false
                for (i in 0 until lastIdx) {
                    val v = lineValue(lines[i], vars)
                    if (v != null) {
                        sum += v
                        any = true
                    }
                }
                return if (any) sum else null
            }
            // Expression on LHS → evaluate standalone.
            return Calculator.evaluate(lhs).getOrNull()
        }

        // Case 2: filled RHS → preserve user edits (live recompute is handled in the UI).
        return null
    }

    /**
     * Running sum of every additive line. Markers (`=`, `Name =` blank RHS, total-keyword
     * assignments) are skipped per [lineValue]. Used by the editor's bottom status bar.
     */
    fun liveTotal(content: String): Double {
        val lines = content.split('\n')
        val vars = LinkedHashMap<String, Double>()
        var sum = 0.0
        for (raw in lines) {
            val v = lineValue(raw, vars)
            if (v != null) sum += v
        }
        return sum
    }

    /**
     * Per-line value extraction. See the file header for the full grammar. Returns null
     * for blank lines, free text, and trigger/marker lines.
     */
    private fun lineValue(raw: String, vars: MutableMap<String, Double>): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val eq = t.indexOf('=')
        if (eq >= 0) {
            val lhs = t.substring(0, eq).trim()
            val rhs = t.substring(eq + 1).trim()
            if (rhs.isEmpty()) return null  // trigger / blank marker
            if (lhs.matches(identRegex)) {
                val v = Calculator.evaluate(rhs, vars).getOrNull() ?: rhs.toDoubleOrNull()
                if (v != null) {
                    vars[lhs.lowercase()] = v
                    // Total-keyword assignments label a closing sum; they don't add to it.
                    return if (lhs.lowercase() in totalKeywords) null else v
                }
            }
        }
        // Whole-line expression: "200 + 15%", "sqrt(16)", "(12+5)*3"
        val whole = Calculator.evaluate(t, vars).getOrNull()
        if (whole != null) return whole
        // Label + expression / number: strip the FIRST word and try the remainder.
        val sp = t.indexOf(' ')
        if (sp > 0) {
            val rest = t.substring(sp + 1).trim()
            val v = Calculator.evaluate(rest, vars).getOrNull()
            if (v != null) return v
            // "Milk 2L 12.5": rest "2L 12.5" doesn't evaluate. Try suffix after the LAST space.
            val lastSp = t.lastIndexOf(' ')
            if (lastSp > sp) {
                val tail = t.substring(lastSp + 1).trim()
                return Calculator.evaluate(tail, vars).getOrNull() ?: tail.toDoubleOrNull()
            }
            return rest.toDoubleOrNull()
        }
        // No spaces, no `=`, didn't evaluate as a whole: it's a bare number or free text.
        return t.toDoubleOrNull()
    }

    /** Word / character counts for the bottom status bar. */
    fun stats(content: String): Stats {
        val text = content.trim()
        val words = if (text.isEmpty()) 0 else text.split(Regex("\\s+")).size
        val chars = content.length
        return Stats(words = words, characters = chars)
    }

    /**
     * True when the note's last non-empty line is a total "trigger" — a bare total keyword
     * (`total`) or a total-keyword assignment with a blank RHS (`total =`). The editor renders
     * a live, non-editable readout (`total = <sum>`) under such a line; it never stores the
     * computed number in the text itself.
     */
    fun isTotalTriggerLine(content: String): Boolean {
        val lines = content.split('\n')
        val lastIdx = lines.indexOfLast { it.isNotBlank() }
        if (lastIdx < 0) return false
        val lastTrim = lines[lastIdx].trim()
        if (!lastTrim.contains("=")) {
            return lastTrim.lowercase() in totalKeywords
        }
        val eq = lastTrim.indexOf('=')
        if (eq < 0) return false
        val lhs = lastTrim.substring(0, eq).trim()
        val rhs = lastTrim.substring(eq + 1).trim()
        // Only a blank RHS counts as a live trigger; a filled RHS means the user has
        // written their own value and we must not override it.
        return rhs.isEmpty() && lhs.lowercase() in totalKeywords
    }

    /**
     * Returns the total-keyword LHS (e.g. "total") of the note's last non-empty trigger line,
     * or null if there is none. Used to auto-append `=` to a bare keyword and to label the
     * readout.
     */
    fun totalTriggerLabel(content: String): String? {
        val lines = content.split('\n')
        val lastIdx = lines.indexOfLast { it.isNotBlank() }
        if (lastIdx < 0) return null
        val lastTrim = lines[lastIdx].trim()
        if (!lastTrim.contains("=")) {
            return if (lastTrim.lowercase() in totalKeywords) lastTrim else null
        }
        val eq = lastTrim.indexOf('=')
        if (eq < 0) return null
        val lhs = lastTrim.substring(0, eq).trim()
        return if (lhs.lowercase() in totalKeywords && lastTrim.substring(eq + 1).trim().isEmpty()) lhs else null
    }

    data class Stats(val words: Int, val characters: Int)
}