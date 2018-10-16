package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class Constructor {

    /// The constructor of all patterns that don't vary by constructor,
    /// e.g. struct patterns and fixed-length arrays.
    //Single
    class Single : Constructor()

    /// Enum variants.
    data class Variant(val variant: RsEnumVariant, val index: Int) : Constructor()

    /// Literal values.
    data class ConstantValue(val value: Constant) : Constructor()

    /// Ranges of literal values (`2...5` and `2..5`).
    data class ConstantRange(val start: Constant, val end: Constant, val includeEnd: Boolean = false) : Constructor()

    /// Array patterns of length n.
    data class Slice(val size: Int) : Constructor()

    fun arity(type: Ty): Int {
        println("<top>.arity(type = $type)")
        return when (this) {
            is Constructor.Single -> type.size
            is Constructor.ConstantValue -> type.subTys().size
            is Constructor.Variant -> variant.size

            is Constructor.ConstantRange -> TODO()
            is Constructor.Slice -> TODO()
        }
    }

    fun coveredByRange(from: Constant, to: Constant, included: Boolean): Boolean {
        return when (this) {
            is Constructor.ConstantValue -> {
                if (included) value >= from && value <= to
                else value >= from && value < to
            }
            is Constructor.ConstantRange -> {
                if (includeEnd) ((end < to) || (included && to == end)) && (start >= from)
                else ((end < to) || (!included && to == end)) && (start >= from)

            }
            Constructor.Single() -> true
            else -> error("Impossible case")
        }
    }

    fun subTys(type: Ty): List<Ty> {
        println("<top>.subTys(type = $type)")
        return when (type) {
            is TyTuple -> {
                type.types
            }
            is TySlice, is TyArray -> when (this) {
                is Constructor.Slice -> {
                    (0..(this.size - 1)).map { type }
                }
                is Constructor.ConstantValue -> listOf()
                else -> error("bad slice pattern")
            }
            is TyReference -> listOf(type.referenced)
            is TyAdt -> {
                // TODO check box
                // TODO ok?
                when (this) {
                    is Constructor.Single -> TODO()
                    is Constructor.Variant -> {
                        this.variant.tupleFields?.tupleFieldDeclList?.map { it.typeReference.type }
                            ?: this.variant.blockFields?.fieldDeclList?.mapNotNull { it.typeReference?.type }
                            ?: run {
                                println("NOTHING IN SUB TYS")
                                listOf<Ty>()
                            }
                    }
                    else -> {
                        println("AAA NOTHING IN SUB TYS")
                        listOf()
                    }
                }
            }
            else -> listOf()
        }
    }
}

fun allConstructors(ty: Ty): List<Constructor> {
    println("<top>.allConstructors(ty = $ty)")
    // TODO check uninhabited
    return when {
        ty === TyBool -> listOf(true, false).map {
            Constructor.ConstantValue(Constant.Boolean(it))
        }
        ty is TyAdt && ty.item is RsEnumItem -> ty.item.enumBody?.enumVariantList?.map {
            Constructor.Variant(it, it.index)
        } ?: emptyList()

        ty is TyArray && ty.size != null -> TODO()
        ty is TyArray || ty is TySlice -> TODO()

        else -> listOf(Constructor.Single())
    }
}



