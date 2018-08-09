package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class Node

enum class Type { TUPLE, STRUCT, EMPTY }

data class Struct(val struct: RsStructItem) : Node()
data class Enum(val enum: RsEnumItem) : Node()
data class EnumVariant(val enum: RsEnumItem, val variant: RsEnumVariant, val type: Type) : Node()

data class Primitive(val ty: TyPrimitive) : Node()
data class Integer(val ty: TyInteger) : Node()
data class Float(val ty: TyFloat) : Node()

class TreeNode(val value: Node) {
    var parent: TreeNode? = null
    var children: MutableList<TreeNode> = mutableListOf()

    fun addChild(node: TreeNode?) {
        node ?: return
        children.add(node)
        node.parent = this
    }

    override fun toString(): String {
        var s = "$value"
        if (!children.isEmpty()) s += " {" + children.map { it.toString() } + "}"
        return s
    }
}



private fun <T : RsFieldsOwner> T.valueType(): Type = when {
    tupleFields != null -> Type.TUPLE
    blockFields != null -> Type.STRUCT
    else -> Type.EMPTY
}

private fun <T : RsFieldsOwner> T.createTreeNodeList(): List<TreeNode> = when (valueType()) {
    Type.TUPLE -> {
        tupleFields?.tupleFieldDeclList?.mapNotNull { buildTreeOfType(it.typeReference.type) }
            ?: emptyList()
    }
    Type.STRUCT -> {
        blockFields?.fieldDeclList?.mapNotNull { buildTreeOfType(it.typeReference?.type ?: return@mapNotNull null) }
            ?: emptyList()
    }
    Type.EMPTY -> {
        emptyList()
    }
}

private fun TreeNode.addEnumTree(enum: RsEnumItem) {
    enum.enumBody?.enumVariantList?.forEach { variant ->
        val variantNode = TreeNode(EnumVariant(enum, variant, variant.valueType()))
        addChild(variantNode)
        variant.createTreeNodeList().forEach(variantNode::addChild)
    }
}

private fun TreeNode.addStructTree(struct: RsStructItem) {
    struct.createTreeNodeList().forEach(::addChild)
}

private fun buildTreeOfType(ty: Ty): TreeNode? {
    return when (ty) {
        is TyPrimitive -> when (ty) {
            is TyNumeric -> when (ty) {
                is TyInteger -> TreeNode(Integer(ty))
                is TyFloat -> TreeNode(Float(ty))
                else -> null /* ignore */
            }
            else -> TreeNode(Primitive(ty))
        }
//        is TyTypeParameter -> {}
        is TyAdt -> {
            when (ty.item) {
                is RsEnumItem -> TreeNode(Enum(ty.item)).apply { addEnumTree(ty.item) }
                is RsStructItem -> TreeNode(Struct(ty.item)).apply { addStructTree(ty.item) }
                else -> null
            }
        }
//        is TyProjection -> {}
//        is TyAnon -> {}
        else -> {
            println("else ty: $ty")
            null
        }
    }
}

private fun TreeNode.getLeafList(): List<TreeNode> {
    val list = mutableListOf<TreeNode>()
    children.forEach {
        if (it.children.isEmpty()) list.add(it)
        else list.addAll(it.getLeafList())
    }
    return list
}

val Node.pattern: String
    get() = when (this) {
        is Integer, is Float -> "_"
        is Enum -> enum.identifier?.text ?: ""
        is EnumVariant -> variant.identifier.text
        is Struct -> struct.identifier?.text ?: ""
        is Primitive -> ty.name
    }

private fun TreeNode.getPattern(pat: String = ""): String {
    return when (value) {
        is Integer -> value.pattern
//        is EnumVariant ->
        else -> ""
    }
}

//private fun TreeNode.getPattern(): String {
//    val parentPattern = parent?.getPattern() ?: ""
//    return when(value) {
//        is Integer, is Float -> {
//            parentPattern + "_"
//        }
//        is EnumVariant -> {
//            parentPattern + "::${value.variant.identifier.text}"
//        }
//        is Enum -> {
//            parentPattern + (value.enum.identifier?.text ?: "" )
//        }
//        else -> parentPattern
//    }
//}

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val ty = o.expr?.type ?: return
            val typeTree = buildTreeOfType(ty) ?: return
            println(typeTree)
            println(typeTree.getLeafList())
            println(typeTree.getLeafList().map { it.getPattern() })

        }
    }


}


