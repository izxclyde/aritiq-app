package com.aritiq.calcnote.calculator

/**
 * Pratt parser producing an AST using operator precedence:
 *   + -    : 1 (left assoc)
 *   * /    : 2 (left assoc)
 *   ^      : 3 (right assoc; matches math convention)
 *
 * % rules (per spec):
 *   A + X%   = A + A*X/100        increment by percent of A
 *   A - X%   = A - A*X/100        decrement by percent of A
 *   A * X%   = A * X/100          scalar percent
 *   A / X%   = A / (X/100)
 *   X% bare  = X/100              standalone (no surrounding binop)
 *
 * Implementation note: the `rhs` flag prevents inner parseExpr calls from greedily
 * binding a trailing `%` as a bare Percent — the binary caller must see it so it
 * can choose PercentOf (add/subtract) vs Binary with Percent (multiply/divide). Parens
 * reset the context to `rhs=false` because they start a fresh expression.
 */
internal sealed class Node {
    data class Num(val value: Double) : Node()
    data class Var(val name: String) : Node()
    data class Unary(val op: Char, val operand: Node) : Node()
    data class Binary(val op: Char, val left: Node, val right: Node) : Node()
    data class Call(val name: String, val args: List<Node>) : Node()
    /** Bare postfix percent: 25% == 0.25. */
    data class Percent(val operand: Node) : Node()
    /** % applied as a modifier of a surrounding binary operator. See file header. */
    data class PercentOf(val op: Char, val base: Node, val pct: Node) : Node()
}

internal class Parser(tokens: List<Token>) {
    private val ts = tokens
    private var pos = 0

    fun parse(): Node {
        pos = 0
        val node = parseExpr(0, rhs = false)
        if (pos != ts.size) throw CalculatorError.Syntax("Trailing tokens at position $pos")
        return node
    }

    private fun peek(): Token? = ts.getOrNull(pos)
    private fun next(): Token = ts[pos++]
    private fun eat(ch: Char) {
        val t = peek() as? Token.CharToken
        if (t == null || t.ch != ch) throw CalculatorError.Syntax("Expected '$ch' at position $pos")
        pos++
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        '^' -> 3
        else -> -1
    }
    private fun rightAssoc(op: Char): Boolean = op == '^'

    private fun parseExpr(minPrec: Int, rhs: Boolean = false): Node {
        var lhs = parseUnary()
        // Standalone postfix %: bind it to the lhs when we're at expression level (not a binary rhs).
        if (!rhs) {
            val t = peek() as? Token.CharToken
            if (t != null && t.ch == '%') {
                pos++
                lhs = Node.Percent(lhs)
            }
        }
        while (true) {
            val t = peek() as? Token.CharToken ?: break
            val op = t.ch
            val p = precedence(op)
            if (p < minPrec) break
            if (op !in "+-*/^") break
            pos++
            val nextP = if (rightAssoc(op)) p else p + 1
            val r = parseExpr(nextP, rhs = true)       // don't bind standalone % here
            val nxt = peek() as? Token.CharToken
            val hasPct = nxt != null && nxt.ch == '%'
            if (hasPct) {
                pos++
                lhs = if (op in "+-") Node.PercentOf(op, lhs, r)
                      else Node.Binary(op, lhs, Node.Percent(r))
            } else {
                lhs = Node.Binary(op, lhs, r)
            }
        }
        return lhs
    }

    private fun parseUnary(): Node {
        val t = peek() as? Token.CharToken
        if (t != null && t.ch == '-') {
            pos++
            return Node.Unary('-', parseUnary())
        }
        if (t != null && t.ch == '+') {
            pos++
            return parseUnary()
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Node {
        val t = next()
        return when (t) {
            is Token.Number -> Node.Num(t.value)
            is Token.Ident -> {
                val open = peek()
                if (open is Token.CharToken && open.ch == '(') {
                    pos++
                    val args = mutableListOf<Node>()
                    val nxt = peek()
                    if (nxt !is Token.CharToken || nxt.ch != ')') {
                        args.add(parseExpr(0, rhs = false))
                        while (peek() is Token.CharToken && (peek() as Token.CharToken).ch == ',') {
                            pos++
                            args.add(parseExpr(0, rhs = false))
                        }
                    }
                    eat(')')
                    Node.Call(t.name.lowercase(), args)
                } else {
                    Node.Var(t.name)
                }
            }
            is Token.CharToken -> {
                if (t.ch == '(') {
                    val e = parseExpr(0, rhs = false)
                    eat(')')
                    e
                } else throw CalculatorError.Syntax("Unexpected '${t.ch}' at position ${pos - 1}")
            }
            else -> throw CalculatorError.Syntax("Unexpected end of input")
        }
    }
}