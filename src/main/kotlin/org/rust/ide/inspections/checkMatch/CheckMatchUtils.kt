package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

internal val RsEnumVariant.index: Int?
    get() = parentEnum.enumBody?.enumVariantList?.indexOf(this)

internal val RsFieldsOwner.size: Int
    get() = tupleFields?.tupleFieldDeclList?.size ?: blockFields?.fieldDeclList?.size ?: 0

internal val List<List<*>?>.width: Int
    get() = maxWith(Comparator.comparingInt { it?.size ?: -1 })?.size ?: 0

internal val List<List<*>?>.height: Int
    get() = size


data class MatchArm(val patterns: List<Pattern>, val arm: RsMatchArm)

internal val RsMatchExpr.matrix: List<MatchArm>
    get() = matchBody?.matchArmList?.flatMap { arm ->
        arm.patList.map { MatchArm(listOf(lowerPattern(it)), arm) }
    } ?: emptyList()


internal val TyAdt.isMarkedNonExhaustive: Boolean
    get() {
        val enum = item as? RsEnumItem ?: return false
        val attrList = enum.outerAttrList
        return attrList.any { it.metaItem.name == "non_exhaustive" }
    }

internal val RsExpr.value: Constant
    get() {
        return when (this) {
            is RsLitExpr -> {
                val kind = kind
                when (kind) {
                    is RsLiteralKind.Boolean -> Constant.Boolean(kind.value)
                    is RsLiteralKind.Integer -> Constant.Integer(kind.value ?: 0)
                    is RsLiteralKind.Float -> Constant.Double(kind.value ?: 0.0)
                    is RsLiteralKind.String -> Constant.String(kind.value ?: "")
                    is RsLiteralKind.Char -> Constant.Char(kind.value ?: "")
                    null -> Constant.Unknown
                }
            }
            is RsPathExpr -> {
                Constant.Path(this)
            }
            is RsUnaryExpr -> TODO()
            else -> TODO()
        }
    }

internal val Ty.size: Int
    get() = when (this) {
        is TyTuple -> {
            this.types.size
        }
        is TyAdt -> {
            val structOrEnum = item
            when (structOrEnum) {
                is RsStructItem -> structOrEnum.size
                is RsEnumItem -> typeArguments.size
                else -> 0
            }
        }
        else -> 0

    }


internal val RsPat.type: Ty
    get() = when (this) {
        is RsPatConst -> expr.type
        is RsPatStruct, is RsPatTupleStruct -> {
            val path = when (this) {
                is RsPatTupleStruct -> path
                is RsPatStruct -> path
                else -> null
            }
            val tmp = path?.reference?.resolve()
            when (tmp) {
                is RsEnumVariant -> TyAdt.valueOf(tmp.parentEnum)
                is RsStructOrEnumItemElement -> TyAdt.valueOf(tmp)
                else -> TyUnknown
            }
        }
        is RsPatWild -> TyUnknown
        is RsPatIdent -> patBinding.type
        is RsPatTup -> TyTuple(patList.map { it.type })
        is RsPatRange -> {
            val type = patConstList.firstOrNull()?.type ?: TyUnknown
            if (patConstList.all { it.type == type }) type
            else TyUnknown
        }

        is RsPatRef -> TODO()
        is RsPatMacro -> TODO()
        is RsPatSlice -> TODO()
        else -> TODO()
    }


internal val RsPat.kind: PatternKind
    get() = when (this) {
        is RsPatIdent -> {
            PatternKind.Binding(patBinding.type)
        }
        is RsPatWild -> {
            PatternKind.Wild
        }
        is RsPatTup -> {
            PatternKind.Leaf(patList.mapIndexed { i, pat ->
                i to lowerPattern(pat)
            })
        }
        is RsPatStruct -> {
            val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
            val subpatterns: List<FieldPattern> = patFieldList.map { patField ->
                val pat = patField.pat
                val binding = patField.patBinding
                val pattern = if (pat != null) {
                    lowerPattern(pat)
                } else {
                    binding?.type?.let { ty ->
                        Pattern(ty, PatternKind.Binding(ty))
                    } ?: error("Binding type = null")
                }
                (item as RsFieldsOwner).indexOf(patField) to pattern
            }

            getLeafOrVariant(item, subpatterns)
        }
        is RsPatTupleStruct -> {
            val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
            val subpatterns: List<FieldPattern> = patList.mapIndexed { i, pat ->
                i to lowerPattern(pat) // TODO patIdent ok?
            }

            getLeafOrVariant(item, subpatterns)
        }
        is RsPatConst -> {
            val ty = this.expr.type
            if (ty is TyAdt) {
                if (ty.item is RsEnumItem) {
                    val variant = (expr as RsPathExpr).path.reference.resolve() as RsEnumVariant
                    PatternKind.Variant(ty, variant.index ?: error("Can't get index"), emptyList())
                } else {
                    error("Unresolved constant")
                }
            } else {
                PatternKind.Constant(expr.value)
            }
        }
        is RsPatRange -> {
            val a = patConstList.first()
            val b = patConstList.last()
            PatternKind.Range(a.expr.value, b.expr.value, (dotdotdot != null) || (dotdoteq != null))
        }
        is RsPatRef -> TODO()
        is RsPatMacro -> TODO()
        is RsPatSlice -> {
            TODO()
        }
        else -> TODO()
    }


internal fun RsFieldsOwner.indexOf(pat: RsPatField): Int {
    val identifier = pat.identifier?.text ?: pat.text
    return namedFields.map { it.identifier.text }.indexOfFirst {
        it == identifier
    }
}


fun getLeafOrVariant(item: RsElement, subpatterns: List<FieldPattern>): PatternKind {
    return when (item) {
        is RsEnumVariant -> PatternKind.Variant(TyAdt.valueOf(item.parentEnum), item.index ?: error("Can't get index"), subpatterns)
        is RsStructItem -> PatternKind.Leaf(subpatterns)
        else -> error("Impossible case $item")
    }
}

fun MutableList<Pattern>.fillWithSubPatterns(subpatterns: List<FieldPattern>) {
    for (subpattern in subpatterns) {
        this[subpattern.first] = subpattern.second
    }
}
