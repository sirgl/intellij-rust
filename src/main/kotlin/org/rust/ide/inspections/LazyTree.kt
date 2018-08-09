package org.rust.ide.inspections

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class Node
object Unknown : Node()

enum class Type { TUPLE, STRUCT, EMPTY }

data class Struct(val struct: RsStructItem) : Node()
data class Enum(val enum: RsEnumItem) : Node()
data class EnumVariant(val enum: RsEnumItem, val variant: RsEnumVariant, val type: Type) : Node()

data class Primitive(val ty: TyPrimitive) : Node()
data class Integer(val ty: TyInteger) : Node()
data class Float(val ty: TyFloat) : Node()

class LazyTree(val value: Node) {
    var parent: LazyTree? = null

    private var isBuild = false
    var children: MutableList<LazyTree> = mutableListOf()
        get(): MutableList<LazyTree> {
            if (isBuild) return field
            field = when (value) {
                is Enum -> getEnumChildren(value.enum)
                is EnumVariant -> getFieldChildren(value.variant)
                is Struct -> getFieldChildren(value.struct)
                is Primitive -> mutableListOf()
                is Integer -> mutableListOf()
                is Float -> mutableListOf()
                is Unknown -> mutableListOf()
            }
            field.forEach { it.parent = this }
            isBuild = true
            return field
        }
        private set

    private fun getEnumChildren(enum: RsEnumItem) =
        enum.enumBody?.enumVariantList?.map { variant ->
            LazyTree(EnumVariant(enum, variant, variant.valueType()))
        }?.toMutableList() ?: mutableListOf()


    private fun getFieldChildren(fieldsOwner: RsFieldsOwner) = when (fieldsOwner.valueType()) {
        Type.TUPLE -> {
            fieldsOwner.tupleFields?.tupleFieldDeclList?.mapNotNull {
                LazyTree(it.typeReference.type.node)
            }?.toMutableList() ?: mutableListOf()
        }
        Type.STRUCT -> {
            fieldsOwner.blockFields?.fieldDeclList?.mapNotNull {
                LazyTree(it.typeReference?.type?.node ?: return@mapNotNull null)
            }?.toMutableList() ?: mutableListOf()
        }
        Type.EMPTY -> {
            mutableListOf()
        }
    }

    private fun <T : RsFieldsOwner> T.valueType(): Type = when {
        tupleFields != null -> Type.TUPLE
        blockFields != null -> Type.STRUCT
        else -> Type.EMPTY
    }
}


val Ty.node: Node
    get() = when (this) {
        is TyPrimitive -> when (this) {
            is TyNumeric -> when (this) {
                is TyInteger -> Integer(this)
                is TyFloat -> Float(this)
                else -> Unknown /* ignore */
            }
            else -> Primitive(this)
        }
        is TyAdt -> {
            when (this.item) {
                is RsEnumItem -> Enum(this.item)
                is RsStructItem -> Struct(this.item)
                else -> Unknown
            }
        }
        else -> {
            println("else this: $this")
            Unknown
        }
    }

