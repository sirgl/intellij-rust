package org.rust.ide.inspections.checkMatch

import com.intellij.openapi.diagnostic.logger
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.fieldTypes
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class Constructor {

    /// The constructor of all patterns that don't vary by constructor,
    /// e.g. struct patterns and fixed-length arrays.
    object Single : Constructor()

    /// Enum variants.
    data class Variant(val variant: RsEnumVariant, val index: Int) : Constructor()

    /// Literal values.
    data class ConstantValue(val value: Constant) : Constructor()

    /// Ranges of literal values (`2...5` and `2..5`).
    data class ConstantRange(val start: Constant, val end: Constant, val includeEnd: Boolean = false) : Constructor()

    /// Array patterns of length n.
    data class Slice(val size: Int) : Constructor()

    fun arity(type: Ty): Int = when (type) {
        is TyTuple -> type.types.size
        is TySlice, is TyArray -> when (this) {
            is Constructor.Slice -> this.size
            is Constructor.ConstantValue -> 0
            else -> error("bad slice pattern")
        }
        is TyReference -> 1
        is TyAdt -> when (type.item) {
            is RsStructItem -> type.item.size
            is RsEnumItem -> {
                val enumVariantList = type.item.enumBody?.enumVariantList
                val index = (this as Variant).index
                enumVariantList?.get(index)?.size ?: 0
            }
            else -> error("bad adt pattern")
        }
        else -> 0
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
            Constructor.Single -> true
            else -> error("Impossible case")
        }
    }

    fun subTys(type: Ty): List<Ty> {
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
                    is Constructor.Single -> {
                        val struct = type.item as RsStructItem
                        struct.fieldTypes
                    }
                    is Constructor.Variant -> variant.fieldTypes
                    else -> listOf()

                }
            }
            else -> listOf()
        }
    }
}

fun allConstructors(ty: Ty): List<Constructor> {
    return when {
        ty === TyBool -> listOf(true, false).map {
            Constructor.ConstantValue(Constant.Boolean(it))
        }
        ty is TyAdt && ty.item is RsEnumItem -> ty.item.enumBody?.enumVariantList?.map {
            Constructor.Variant(it, it.index ?: error("Can't get index"))
        } ?: emptyList()

        ty is TyArray && ty.size != null -> TODO()
        ty is TyArray || ty is TySlice -> TODO()

        else -> listOf(Constructor.Single)
    }
}



