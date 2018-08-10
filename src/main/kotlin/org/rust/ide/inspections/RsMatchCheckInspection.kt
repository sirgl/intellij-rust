package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val ty = o.expr?.type ?: return
            val tree = TyTree(ty.toNode)
            val listPatList = o.matchBody?.matchArmList?.map { it.patList }
            val patTrees: MutableList<PatTree> = mutableListOf()
            listPatList?.forEach { patList ->
                patList.forEach { pat ->
                    patTrees.add(buildPatTree(pat))
                    println(patTrees.last())
                }
            }
        }
    }
}

fun RsPath.toNode(): Node {
    val res = reference.resolve() ?: return Unknown
    when (res) {
        is RsEnumVariant -> {
            val enum = path?.toNode() as? Enum ?: return Unknown
            return EnumVariant(enum, res, res.valueType())
        }
        is RsEnumItem -> {
            return Enum(res)
        }
        else -> return Unknown
    }
}

fun buildPatTree(pat: RsPat): PatTree {
    when (pat) {
        is RsPatIdent -> {
            if (pat.pat == null) {
                return PatTree(Binding(pat.patBinding))
            }
        }
        is RsPatStruct -> {
            val node = pat.path.toNode()
            when (node) {
                is EnumVariant -> {
                    return PatTree(node).also { tree ->
                        pat.patFieldList.forEach {
                            tree.addChild(buildPatTree(it.pat ?: return@forEach))
                        }
                    }
                }
            }
        }
        is RsPatTupleStruct -> {
            val node = pat.path.toNode()
            when (node) {
                is EnumVariant -> {
                    return PatTree(node).also { tree ->
                        pat.patList.forEach {
                            tree.addChild(buildPatTree(it))
                        }
                    }
                }
            }
        }
        is RsPatWild -> return PatTree(Wild(pat))
        is RsPatConst -> {
            val pathList = pat.expr.children.filter { it as? RsPath != null }.map { it as RsPath }
            if (pathList.size != 1) return PatTree(Unknown)
            val path = pathList.first()
            val node = path.toNode()
            when (node) {
                is EnumVariant -> {
                    return PatTree(node)
                }
                is Struct -> {
                }//TODO
                else -> return PatTree(Unknown)
            }
        }
    }
    return PatTree(Unknown)
}

class PatTree(val value: Node) {
    var parent: PatTree? = null
    var children: MutableList<PatTree> = mutableListOf()

    fun addChild(node: PatTree?) {
        node ?: return
        node.parent = this
        children.add(node)
    }

    override fun toString(): String {
        var s = "$value"
        if (!children.isEmpty()) s += " {" + children.map { it.toString() } + "}"
        return s
    }
}
