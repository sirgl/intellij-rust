package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

val RsEnumVariant.index: Int
    get() = parentEnum.enumBody?.enumVariantList?.indexOf(this) ?: -1

val RsFieldsOwner.size: Int
    get() = tupleFields?.tupleFieldDeclList?.size ?: blockFields?.fieldDeclList?.size ?: 0

val List<List<*>?>.width: Int
    get() = maxWith(Comparator.comparing<List<*>, Int> { it?.size ?: -1 })?.size ?: 0

val List<List<*>?>.height: Int
    get() = size

//val RsMatchExpr.matrix: List<Pair<List<Pattern>, RsMatchArmGuard?>>
//    get() = matchBody?.matchArmList?.map { arm ->
//        arm.patList.map { lowerPattern(it) } to arm.matchArmGuard
//    } ?: emptyList()
val RsMatchExpr.matrix: List<Pair<List<Pattern>, RsMatchArmGuard?>>
    get() = matchBody?.matchArmList?.flatMap { arm ->
        arm.patList.map { listOf(lowerPattern(it, arm)) to arm.matchArmGuard }
    } ?: emptyList()


val TyAdt.isNonExhaustiveEnum: Boolean
    get() {
        val enum = item as? RsEnumItem ?: return false
        val attrList = enum.outerAttrList
        return attrList.any { it.metaItem.name == "non_exhaustive" }
    }

val RsExpr.value: Constant
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

val Ty.size: Int
    get() {
        println("<top>.Ty.size() ty=$this")
        return when (this) {
            is TyTuple -> {
                println("<top>.Ty.size() this is type ${types.size} ")
                this.types.size
            }
            is TyAdt -> {
                println("<top>.Ty.size() this is adt adt=$this")
                val structOrEnum = item
                when (structOrEnum) {
                    is RsStructItem -> structOrEnum.size
                    is RsEnumItem -> typeArguments.size
                    else -> 0
                }
            }
            else -> 0

        }
    }


val RsPat.type: Ty
    get() {
        return when (this) {
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
            is RsPatUniq -> TODO()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> TODO()
        }
    }

val RsPat.kind: PatternKind
    get() {
        println("<top>.RsPat.kind()")
        return when (this) {
            is RsPatIdent -> {
                println("<top>.RsPat.kind() RsPatIdent")
                PatternKind.Binding(this.patBinding.type)
            }
            is RsPatWild -> {
                println("<top>.RsPat.kind() RsPatWild")
                PatternKind.Wild
            }
            is RsPatTup -> {
                println("<top>.RsPat.kind() RsPatTup")
                PatternKind.Leaf(this.patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat, null)
                })
            }
            is RsPatStruct -> {
                println("<top>.RsPat.kind() RsPatStruct")
                val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
                val subpatterns: List<FieldPattern> = patFieldList.map { patField ->
                    val pat = patField.pat
                    val binding = patField.patBinding
                    val pattern = if (pat != null) {
                        lowerPattern(pat, null)
                    } else {
                        binding?.type?.let { ty ->
                            Pattern(ty, PatternKind.Binding(ty), null)
                        } ?: error("Binding type = null")
                    }
                    (item as RsFieldsOwner).indexOf(patField) to pattern
                }

                getLeafOrVariant(item, subpatterns)
            }
            is RsPatTupleStruct -> {
                println("<top>.RsPat.kind() RsPatTupleStruct")
                val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
                val subpatterns: List<FieldPattern> = patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat, null) // TODO patIdent ok?
                }

                getLeafOrVariant(item, subpatterns)
            }
            is RsPatConst -> {
                println("<top>.RsPat.kind() RsPatConst")
                val ty = this.expr.type
                if (ty is TyAdt) {
                    println("<top>.RsPat.kind() RsPatConst.expr is TyAdt ")
                    if (ty.item is RsEnumItem) {
                        println("<top>.RsPat.kind() RsPatConst.expr it EnumVariant")
                        val variant = (expr as RsPathExpr).path.reference.resolve() as RsEnumVariant
                        PatternKind.Variant(ty, variant.index, emptyList())
                    } else {
                        error("Unresolved constant")
                    }
                } else {
                    PatternKind.Constant(expr.value)
                }
            }
            is RsPatRange -> {
                println("<top>.RsPat.kind() RsPatRange")
                val a = this.patConstList.first()
                val b = this.patConstList.last()
                PatternKind.Range(a.expr.value, b.expr.value, (dotdotdot != null) || (dotdoteq != null))
            }
            is RsPatRef -> TODO()
            is RsPatUniq -> TODO()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> TODO()
        }

    }

fun RsFieldsOwner.indexOf(pat: RsPatField): Int {
    val identifier = pat.identifier
    return namedFields.map { it.identifier }.indexOfFirst {
        it.text == identifier?.text
    }
}

fun Ty.subTys(): List<Ty> {
    println("<top>.subTys() ty=$this")
    return when (this) {
        is TyTuple -> {
            println("<top>.subTys this is tuple subty=${this.types}")
            this.types
        }
        is TyAdt -> {
            println("<top>.subTys this is adt subty=$typeArguments")
            this.typeArguments
        }
        else -> {
            println("<top>.subTys this is something subty=${emptyList<Ty>()}")
            emptyList()
        }
    }
}

