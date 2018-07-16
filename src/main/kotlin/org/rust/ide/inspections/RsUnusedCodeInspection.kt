package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.cfg.RsResultControlFlowVisitor
import org.rust.lang.core.cfg.getControlFlow
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsVisitor

class RsUnusedCodeInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : RsVisitor() {
            override fun visitFunction(function: RsFunction) {
                val block = function.block ?: return
                val controlFlow = getControlFlow(block)
                object: RsResultControlFlowVisitor<>
            }
        }
}
