package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsVisitor

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitExprStmt(o: RsExprStmt) {
            o as? RsMatchExpr ?: return
        }
    }
}
