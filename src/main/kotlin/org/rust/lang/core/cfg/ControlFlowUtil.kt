package org.rust.lang.core.cfg

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsLabeledExpression

fun RsBreakExpr.findExitedExpr() : RsExpr? {
    // TODO label
//    val label = label?.reference?.resolve() as? RsLabeledExpression
    var current = parent
    while (current !is RsLoopExpr && current !is RsForExpr && current !is RsWhileExpr) {
        current = current.parent
    }
    return current as? RsExpr
}

fun RsBinaryExpr.isShortCircuit() : Boolean {
    return binaryOp.oror != null || binaryOp.andand != null
}

fun getControlFlow(block: RsBlock): RsControlFlow {
    return CachedValuesManager.getCachedValue(block, {
        CachedValueProvider.Result.create(
            ControlFlowBuilder().buildControlFlow(block),
            PsiModificationTracker.MODIFICATION_COUNT
        )
    })
}
