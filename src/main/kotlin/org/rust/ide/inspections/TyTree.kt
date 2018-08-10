package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class Node(val identifier: PsiElement? = null, var check: Boolean = false)
object Unknown : Node()

enum class FieldsType { TUPLE, STRUCT, EMPTY }

data class Struct(val struct: RsStructItem) : Node(struct.identifier)
data class Enum(val enum: RsEnumItem) : Node(enum.identifier)
data class EnumVariant(val enum: Enum, val variant: RsEnumVariant, val type: FieldsType) : Node(variant.identifier)

sealed class Primitive(val ty: TyPrimitive) : Node()
class SimplePrimitive(ty: TyPrimitive) : Primitive(ty)
class Integer(ty: TyInteger) : Primitive(ty)
class Float(ty: TyFloat) : Primitive(ty)

data class Binding(val binding: RsPatBinding) : Node()
data class Wild(val binding: RsPatWild) : Node()

class TyTree(val value: Node) {
    var parent: TyTree? = null

    val children: MutableList<TyTree> by lazy {
        val list = when (value) {
            is Enum -> getEnumChildren(value.enum)
            is EnumVariant -> getFieldChildren(value.variant)
            is Struct -> getFieldChildren(value.struct)
            is Primitive -> mutableListOf()
            is Integer -> mutableListOf()
            is Float -> mutableListOf()
            is Unknown -> mutableListOf()
            is Binding, is Wild -> mutableListOf()
        }
        list.forEach { it.parent = this }
        list
    }

    private fun getEnumChildren(enum: RsEnumItem) =
        enum.enumBody?.enumVariantList?.map { variant ->
            TyTree(EnumVariant(Enum(enum), variant, variant.valueType()))
        }?.toMutableList() ?: mutableListOf()

    private fun getFieldChildren(fieldsOwner: RsFieldsOwner) = when (fieldsOwner.valueType()) {
        FieldsType.TUPLE -> {
            fieldsOwner.tupleFields?.tupleFieldDeclList?.mapNotNull {
                TyTree(it.typeReference.type.toNode)
            }?.toMutableList() ?: mutableListOf()
        }
        FieldsType.STRUCT -> {
            fieldsOwner.blockFields?.fieldDeclList?.mapNotNull {
                TyTree(it.typeReference?.type?.toNode ?: return@mapNotNull null)
            }?.toMutableList() ?: mutableListOf()
        }
        FieldsType.EMPTY -> {
            mutableListOf()
        }
    }


}

fun <T : RsFieldsOwner> T.valueType(): FieldsType = when {
    tupleFields != null -> FieldsType.TUPLE
    blockFields != null -> FieldsType.STRUCT
    else -> FieldsType.EMPTY
}

val Ty.toNode: Node
    get() = when (this) {
        is TyPrimitive -> when (this) {
            is TyNumeric -> when (this) {
                is TyInteger -> Integer(this)
                is TyFloat -> Float(this)
                else -> Unknown /* ignore */
            }
            else -> SimplePrimitive(this)
        }
        is TyAdt -> when (this.item) {
            is RsEnumItem -> Enum(this.item)
            is RsStructItem -> Struct(this.item)
            else -> Unknown
        }
        else -> {
            println("else this: $this")
            Unknown
        }
    }
