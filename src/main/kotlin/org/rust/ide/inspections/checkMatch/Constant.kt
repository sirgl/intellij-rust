package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsPathExpr

sealed class Constant {
    data class Boolean(val value: kotlin.Boolean) : Constant()
    data class Integer(val value: Int) : Constant()
    data class Double(val value: kotlin.Double) : Constant()
    data class String(val value: kotlin.String) : Constant()
    data class Char(val value: kotlin.String) : Constant()
    data class Path(val value: RsPathExpr) : Constant()
    object Unknown : Constant()


    operator fun compareTo(other: Constant): Int {
        return when {
            this is Constant.Boolean && other is Constant.Boolean -> when {
                this.value == other.value -> 0
                this.value && !other.value -> 1
                else -> -1
            }
            this is Constant.Integer && other is Constant.Integer -> this.value.compareTo(other.value)

            this is Constant.Double && other is Constant.Double -> this.value.compareTo(other.value)

            this is Constant.String && other is Constant.String -> this.value.compareTo(other.value)

            this is Constant.Char && other is Constant.Char -> this.value.compareTo(other.value)

            this is Constant.Path && other is Constant.Path -> {
                val aR = this.value.path.reference.resolve()
                val bR = other.value.path.reference.resolve()
                if (aR?.equals(bR) == true) 0
                else -1
            }
            else -> -1
        }
    }
}
