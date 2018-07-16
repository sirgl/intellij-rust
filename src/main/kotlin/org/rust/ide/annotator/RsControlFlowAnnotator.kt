package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.lang.core.cfg.*
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.returnType
import org.rust.lang.core.types.ty.TyUnit

class RsControlFlowAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Not actually needed
        val function = element as? RsFunction ?: return
        val block = function.block ?: return
        val rbrace = block.rbrace ?: return
        if (function.returnType === TyUnit) return
        val controlFlow: RsControlFlow = getControlFlow(block)
        val lastIndex = controlFlow.instructions.lastIndex
        val visitor = object: RsResultControlFlowVisitor<Boolean>() {
            override var result: Boolean = false

            override fun visitSimpleInstruction(instruction: SimpleInstruction, index: Int) {
                if (index == lastIndex) {
                    result = false
                }
            }

            override fun shouldContinueBypass(): Boolean {
                return result
            }
        }
        controlFlow.bypass(visitor)

        holder.createErrorAnnotation(rbrace, "Missing return")
    }
}
