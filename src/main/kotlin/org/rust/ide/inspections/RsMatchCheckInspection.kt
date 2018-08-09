package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.type

val Node.pattern: String
    get() = when (this) {
        is Integer, is Float -> "_"
        is Enum -> enum.identifier?.text ?: ""
        is EnumVariant -> variant.identifier.text
        is Struct -> struct.identifier?.text ?: ""
        is Primitive -> ty.name
        is Unknown -> ""
    }

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val ty = o.expr?.type ?: return
        }
    }
}


