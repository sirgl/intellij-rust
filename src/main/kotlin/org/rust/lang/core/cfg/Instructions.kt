package org.rust.lang.core.cfg

import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.RsElement


sealed class RsInstruction {
    abstract override fun toString(): String
    abstract fun accept(visitor: RsControlFlowVisitor, position: Int)
}

sealed class BranchingInstruction(var targetIndex: Int) : RsInstruction()

sealed class SimpleInstruction : RsInstruction()

sealed class FinishingInstruction(val element: RsElement) : RsInstruction()

// Simple instructions

class EmptyInstruction : SimpleInstruction() {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitEmptyInstruction(this, position)
    }

    override fun toString() = "EMPTY"
}

class VariableDeclInstruction(val variable: RsPatIdent) : SimpleInstruction(){
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitVariableDeclInstruction(this, position)
    }

    override fun toString() = "DECL ${variable.patBinding.identifier.text}"
}

class VariableReadInstruction(val variable: RsPatIdent) : SimpleInstruction() {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitVariableReadInstruction(this, position)
    }
    override fun toString() = "READ ${variable.patBinding.identifier.text}"
}

class VariableWriteInstruction(val variable: RsPatIdent) : SimpleInstruction() {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitVariableWriteInstruction(this, position)
    }
    override fun toString() = "WRITE ${variable.patBinding.identifier.text}"
}

// Branching instructions

class GoToInstruction(targetIndex: Int) : BranchingInstruction(targetIndex) {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitGoToInstruction(this, position)
    }
    override fun toString() = "GOTO $targetIndex"
}

class ConditionalGoToInstruction(targetIndex: Int) : BranchingInstruction(targetIndex) {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitConditionalGoToInstruction(this, position)
    }
    override fun toString() = "CONDGOTO $targetIndex"
}

// Finishing instructions

class DivergingInstruction(element: RsElement) : FinishingInstruction(element) {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitDivergingInstruction(this, position)
    }
    override fun toString() = "DIVERGE"
}

class ReturnInstruction(element: RsElement) : FinishingInstruction(element) {
    override fun accept(visitor: RsControlFlowVisitor, position: Int) {
        visitor.visitReturnInstruction(this, position)
    }

    override fun toString() = "RETURN"
}
