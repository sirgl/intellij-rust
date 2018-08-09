package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

sealed class MyNode

enum class Type { TUPLE, STRUCT, EMPTY }

data class Struct(val struct: RsStructItem) : MyNode()
data class Enum(val enum: RsEnumItem) : MyNode()
data class EnumVariant(val enum: RsEnumItem, val variant: RsEnumVariant, val type: Type) : MyNode()

data class Integer(val ty: TyInteger) : MyNode()
data class Float(val ty: TyFloat) : MyNode()


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
            else -> null
        }
        is TyAdt -> {
            when (ty.item) {
                is RsEnumItem -> TreeNode(Enum(ty.item)).apply { addEnumTree(ty.item) }
                is RsStructItem -> TreeNode(Struct(ty.item)).apply { addStructTree(ty.item) }
                else -> null
            }
        }
        else -> {
            null
        }
    }
}


class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val ty = o.expr?.type ?: return
            println(buildTreeOfType(ty))
        }
    }

    /*private fun debugPat(pat: RsPat): String {
        return when (pat) {
            is RsPatSlice -> "slice[${pat.dotdot};patList{" + pat.patList.joinToString { debugPat(it) } + "}]"

            is RsPatTupleStruct ->
                "tupleStruct[${pat.dotdot};${pat.path};patList{" + pat.patList.joinToString { debugPat(it) } + "}]"
            is RsPatIdent ->
                "ident[${pat.at};${pat.pat?.let(::debugPat)};${pat.patBinding}]"

            is RsPatRef -> "ref[${pat.and};${pat.mut};${debugPat(pat.pat)}]"

            is RsPatTup -> "tup[${pat.dotdot};patList{" + pat.patList.joinToString { debugPat(it) } + "}]"

            is RsPatStruct -> "struct[${pat.dotdot};${pat.path};patFieldList{" + pat.patFieldList.joinToString { it.text } + "}]"

            is RsPatConst -> "const[${pat.expr}(${pat.expr.text})]"

            is RsPatMacro -> "macro[${pat.macroCall}(${pat.macroCall.text})]"

            is RsPatUniq -> "uniq[unimpl]"

            is RsPatWild -> "wild[${pat.underscore}]"

            is RsPatRange -> "range[unimpl]"

            else -> ""

        }
    }*/
}

class TreeNode(var value: MyNode) {
    var parent: TreeNode? = null
    var children: MutableList<TreeNode> = mutableListOf()

    fun addChild(node: TreeNode?) {
        node ?: return
        children.add(node)
        node.parent = this
    }

    override fun toString(): String {
        var s = "$value"
        if (!children.isEmpty()) s += " {\n\t" + children.map { it.toString() } + " \n}"
        return s
    }
}
