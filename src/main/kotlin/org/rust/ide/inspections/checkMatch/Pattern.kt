package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple

data class Pattern(val ty: Ty, val kind: PatternKind) {
    override fun toString(): String {
        return when (kind) {
            PatternKind.Wild -> "_"
            is PatternKind.Binding -> "x"
            is PatternKind.Variant -> {
                val enum = kind.ty.item as RsEnumItem
                val variant = enum.enumBody?.enumVariantList?.get(kind.variantIndex) ?: return ""
                "${enum.identifier?.text ?: ""}::${variant.identifier.text}" + if(!kind.subpatterns.isEmpty()) {
                    "(${kind.subpatterns.sortedBy { it.first }.joinToString { it.second.toString() }})"
                } else ""
            }
            is PatternKind.Leaf -> {
                val subpatterns = kind.subpatterns.sortedBy { it.first }
                when (ty) {
                    is TyTuple -> {
                        subpatterns.joinToString(", ", "(", ")") { pattern ->
                            pattern.second.toString()
                        }
                    }
                    is TyAdt -> {
                        val struct = ty.item as RsStructItem
                        (struct.identifier?.text ?: "") + when {
                            struct.blockFields != null -> {
                                subpatterns.joinToString(",", "{", "}") { pattern ->
                                    "${struct.blockFields?.fieldDeclList?.get(pattern.first)?.identifier?.text}: ${pattern.second}"
                                }
                            }
                            struct.tupleFields != null -> {
                                subpatterns.joinToString(",", "(", ")") { pattern ->
                                    "${pattern.second}"
                                }
                            }
                            else -> error("struct has no fields")
                        }
                    }
                    else -> ""
                }
            }
            is PatternKind.Range -> "${kind.lc}${if(kind.included) ".." else "..."}${kind.rc}"
            is PatternKind.Deref -> "&${kind.subpattern}"
            is PatternKind.Constant -> kind.value.toString()
            is PatternKind.Slice -> TODO()
            is PatternKind.Array -> TODO()
        }
    }

    val constructors: List<Constructor>?
        get() {
            return when (kind) {
                PatternKind.Wild, is PatternKind.Binding -> null
                is PatternKind.Variant -> {
                    val enum = kind.ty.item as RsEnumItem
                    val variant = enum.enumBody?.enumVariantList?.get(kind.variantIndex) ?: return emptyList()
                    listOf(Constructor.Variant(variant, kind.variantIndex))
                }
                is PatternKind.Leaf, is PatternKind.Deref -> listOf(Constructor.Single)
                is PatternKind.Constant -> listOf(Constructor.ConstantValue(kind.value))
                is PatternKind.Range -> listOf(Constructor.ConstantRange(kind.lc, kind.rc, kind.included))
                is PatternKind.Slice -> TODO()
                is PatternKind.Array -> TODO()
            }
        }
}

typealias FieldPattern = Pair<Int, Pattern>

fun lowerPattern(pat: RsPat): Pattern {
    val kind = pat.kind
    val ty = pat.type
    return Pattern(ty, kind)
}
