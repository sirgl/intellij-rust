package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsPathExpr

sealed class Constant {
    data class Boolean(val value: kotlin.Boolean) : Constant() {
        override fun toString() = value.toString()
    }

    data class Integer(val value: Number) : Constant() {
        override fun toString() = value.toString()
    }

    data class Double(val value: kotlin.Double) : Constant() {
        override fun toString() = value.toString()
    }

    data class String(val value: kotlin.String) : Constant() {
        override fun toString() = value
    }

    data class Char(val value: kotlin.String) : Constant() {
        override fun toString() = value
    }

    data class Path(val value: RsPathExpr) : Constant() {
        override fun toString() = value.toString()
    }

    object Unknown : Constant()


    operator fun compareTo(other: Constant): Int {
        return when {
            this is Constant.Boolean && other is Constant.Boolean -> when {
                value == other.value -> 0
                value && !other.value -> 1
                else -> -1
            }
            this is Constant.Integer && other is Constant.Integer -> value.toLong().compareTo(other.value.toLong())

            this is Constant.Double && other is Constant.Double -> value.compareTo(other.value)

            this is Constant.String && other is Constant.String -> value.compareTo(other.value)

            this is Constant.Char && other is Constant.Char -> value.compareTo(other.value)

            this is Constant.Path && other is Constant.Path -> {
                val aR = value.path.reference.resolve()
                val bR = other.value.path.reference.resolve()
                if (aR?.equals(bR) == true) 0
                else -1
            }
            else -> -1
        }
    }
}
