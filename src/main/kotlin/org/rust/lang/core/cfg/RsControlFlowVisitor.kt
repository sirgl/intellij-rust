package org.rust.lang.core.cfg

import java.util.*

abstract class RsControlFlowVisitor {
    open fun visitInstruction(instruction: RsInstruction, index: Int) {

    }

    open fun visitBranchingInstruction(instruction: BranchingInstruction, index: Int) {
        visitInstruction(instruction, index)
    }

    open fun visitSimpleInstruction(instruction: SimpleInstruction, index: Int) {
        visitInstruction(instruction, index)
    }

    open fun visitFinishingInstruction(instruction: FinishingInstruction, index: Int) {
        visitInstruction(instruction, index)
    }

    // Simple instructions

    open fun visitEmptyInstruction(instruction: EmptyInstruction, index: Int) {
        visitSimpleInstruction(instruction, index)
    }

    open fun visitVariableDeclInstruction(instruction: VariableDeclInstruction, index: Int) {
        visitSimpleInstruction(instruction, index)
    }

    open fun visitVariableReadInstruction(instruction: VariableReadInstruction, index: Int) {
        visitSimpleInstruction(instruction, index)
    }

    open fun visitVariableWriteInstruction(instruction: VariableWriteInstruction, index: Int) {
        visitSimpleInstruction(instruction, index)
    }

    // Branching instructions

    open fun visitGoToInstruction(instruction: GoToInstruction, index: Int) {
        visitBranchingInstruction(instruction, index)
    }

    open fun visitConditionalGoToInstruction(instruction: ConditionalGoToInstruction, index: Int) {
        visitBranchingInstruction(instruction, index)
    }

    // Finishing instructions

    open fun visitReturnInstruction(instruction: ReturnInstruction, index: Int) {
        visitFinishingInstruction(instruction, index)
    }

    open fun visitDivergingInstruction(instruction: DivergingInstruction, index: Int) {
        visitFinishingInstruction(instruction, index)
    }
}

abstract class RsResultControlFlowVisitor<T> : RsControlFlowVisitor() {
    abstract val result: T?

    open fun shouldContinueBypass()  = true
}

//abstract class MarkingVisitor : RsControlFlowVisitor() {
//    abstract val processedInstructions : BitSet
//}
