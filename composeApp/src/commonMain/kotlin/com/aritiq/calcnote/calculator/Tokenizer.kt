package com.aritiq.calcnote.calculator

/**
 * Tokens emitted by [Tokenizer]. NUMBER carries its parsed Double value.
 * Variables are looked up at evaluation time, so IDENT keeps its name.
 */
internal sealed class Token {
    data class Number(val value: Double) : Token()
    data class Ident(val name: String) : Token()
    data class CharToken(val ch: Char) : Token()        // + - * / ^ ( ) , %
}

/**
 * Lexer. Splits an expression string into tokens. Recognizes:
 * - double literals incl. thousands separators: 1,200, 1200, 25.5 (1L+3R form, e.g. `1,000,000`)
 * - identifiers: salary, RENT, food
 * - function/constant names handled by the parser as IDENT-with-paren
 * - operators: + - * / ^ %
 * - parentheses, comma (function arg separator)
 *
 * Whitespace between operands and operators is ignored (12 + 5 == 12+5).
 *
 * ponytail: thousands separator disambiguation is steered to the canonical
 * "comma followed by exactly 3 digits then a non-digit" form so `2,3` in
 * `max(2,3)` parses as two args — calculator patterns like `2,3` and `2,30`
 * don't accidentally collapse into `23` / `230`.
 */
internal class Tokenizer(private val src: String) {
    private val out = ArrayList<Token>()
    private var i = 0

    fun tokenize(): List<Token> {
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> readNumber(c)
                c.isLetter() || c == '_' -> readIdent(c)
                c in "+-*/^%()," -> {
                    out.add(Token.CharToken(c))
                    i++
                }
                else -> throw CalculatorError.Syntax("Unexpected character: '$c' at position $i")
            }
        }
        return out
    }

    private fun readNumber(first: Char) {
        var s = "$first"
        i++
        while (i < src.length) {
            val c = src[i]
            if (c.isDigit() || c == '.') {
                s += c
                i++
            } else if (c == ',' && isValidThousandsSep(i)) {
                i++ // skip; digits already in s
            } else break
        }
        out.add(Token.Number(s.toDouble()))
    }

    /** A thousands separator: comma followed by exactly 3 digits then a non-digit (or end). */
    private fun isValidThousandsSep(pos: Int): Boolean {
        if (pos == 0 || !src[pos - 1].isDigit()) return false
        if (pos + 4 > src.length) return false
        if (!src[pos + 1].isDigit()) return false
        if (!src[pos + 2].isDigit()) return false
        if (!src[pos + 3].isDigit()) return false
        return pos + 4 >= src.length || !src[pos + 4].isDigit()
    }

    private fun readIdent(first: Char) {
        var s = "$first"
        i++
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) {
            s += src[i]
            i++
        }
        out.add(Token.Ident(s))
    }
}