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
            val root = PatTree(ty.toNode)
            listPatList?.forEach { patList ->
                patList.forEach { pat ->
                    buildPatternTree(root, pat)
                    println(root)
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


//TODO проверить существование ветки
// Придумать способ идентифицировать параметры в листе
fun buildPatternTree(root: PatTree, pat: RsPat) {
    when (pat) {
        is RsPatWild -> {
            root.addChild(PatTree(Wild(pat)))
            return
        }
        is RsPatIdent -> {
            // Maybe exist some other case?
            root.addChild(PatTree(Binding(pat.patBinding)))
        }
        is RsPatConst -> {
            val expr = pat.expr
            when (expr) {
                is RsLitExpr -> {
                    root.addChild(PatTree(expr.type.toNode))
                }
                is RsPathExpr -> {
                    root.addChild(PatTree(expr.path.toNode()))
                }
            }
        }
        is RsPatStruct -> {
            val node = pat.path.toNode()
            //TODO для структуры
            if (node is EnumVariant && root.value == node.enum) {
                val child = PatTree(node)
                root.addChild(child)
                pat.patFieldList.forEach { patField ->
                    if (patField.pat == null) throw Exception("PatField ${patField.text} is null!") //Возможно?
                    buildPatternTree(child, patField.pat ?: return@forEach)
                }
            }
        }
        is RsPatTupleStruct -> {
            val node = pat.path.toNode()
            //TODO для структуры
            if (node is EnumVariant && root.value == node.enum) {
                var child = root.children.find { it.value == node }
                if (child == null) {
                    child = PatTree(node)
                    root.addChild(child)
                }

                pat.patList.forEach { pattern ->
                    buildPatternTree(child, pattern)
                }
            }
        }
    }
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
