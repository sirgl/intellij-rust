package org.rust.lang.core.cfg

import gnu.trove.TIntArrayList
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.type
import java.util.*

private data class LabelDeclInfo(val labelDecl: RsLabelDecl, val loopStartIndex: Int)


private const val NOT_INITIALIZED = -1

class ControlFlowBuilder : RsVisitor() {
    val instructions = mutableListOf<RsInstruction>()

    // To handle continue by label we need to maintain stack of (label decl, loop start index) to find quickly index
    // of instruction referenced by label inside of loop body
    private val labelStack: Deque<LabelDeclInfo> = ArrayDeque<LabelDeclInfo>()

    // Needed for continue statement inside loop
    private val loopStartIndexStack = TIntArrayList()
    // At the break element index of target instruction is unknown (loop not finished yet)
    // All break instructions (without label) handled at the end of every loop
    // TODO replace with general forward jump stack
    private val pendingBreakInstructionIndices = TIntArrayList()
    // as well as for labeled breaks
    private val pendingLabeledBreakInstructions = mutableMapOf<RsLabel, Int>()

    fun buildControlFlow(block: RsBlock) : RsControlFlow {
        visitBlock(block)
        instructions.add(EmptyInstruction())
        return RsControlFlowImpl(instructions)
    }

    override fun visitBlock(block: RsBlock) {
        for (stmt: RsStmt in block.stmtList) {
            generate(stmt)
        }
        val expr = block.expr
        if (expr != null) {
            generate(expr)
        }
    }

    override fun visitStmt(stmt: RsStmt) {
        when (stmt) {
            is RsEmptyStmt -> emit(EmptyInstruction())
            is RsExprStmt -> generate(stmt.expr)
        }
    }

    private fun emit(instruction: RsInstruction) {
        instructions.add(instruction)
    }

    private fun generate(element: RsElement) {
        element.accept(this)
    }

    override fun visitParenExpr(o: RsParenExpr) {
        generate(o.expr)
    }

    // as at the end empty instruction will be inserted, next instruction always present
    private fun nextInstructionIndex() = instructions.size

    override fun visitLetDecl(o: RsLetDecl) {
        val pat = o.pat as? RsPatIdent ?: return // TODO not only PatIdent
        emit(VariableDeclInstruction(pat))
        val initializer = o.expr ?: return
        generate(initializer)
        emit(VariableWriteInstruction(pat))
    }

    // This method supposed to use only in read position. Writing to path is handled separately in binary expr
    // TODO x.y.z = 12; // read for all
    override fun visitPathExpr(o: RsPathExpr) {
        val ident = o.path.resolvePatIdent() ?: return
        emit(VariableReadInstruction(ident))
//        o.parent
        // Ensure we are not the right side of assignment
//        val parentPath = PsiTreeUtil.getParentOfType(o, RsPathExpr::class.java, false, RsBinaryExpr::class.java)
//        if (parentPath != null) {
//            parentPath.parent is RsBinaryExpr
//        }
    }

    override fun visitCallExpr(call: RsCallExpr) {
        if (call.type === TyNever) {
            emit(DivergingInstruction(call))
            return
        }
        generate(call.valueArgumentList)
    }

    override fun visitValueArgumentList(argumentList: RsValueArgumentList) {
        for (expr in argumentList.exprList) {
            generate(expr)
        }
    }

    override fun visitIfExpr(o: RsIfExpr) {
        val condition = o.condition
        val thenBranch = o.block?.expr
        val elseBranch = o.elseBranch
        if (condition == null) {
            // To support error recovery, we should still generate branch if it exists to avoid highlighting
            // until condition appeared
            if (thenBranch != null) {
                generate(thenBranch)
            }
        } else {
            generate(condition)
            val conditionToElseInstruction = ConditionalGoToInstruction(NOT_INITIALIZED)
            emit(conditionToElseInstruction)

            if (thenBranch == null) {
                if (elseBranch != null) {
                    // strange case:  if cond else { elseBranch }
                    // condition goto instruction points to next instruction after elseBranch
                    generate(elseBranch)
                }
                conditionToElseInstruction.targetIndex = nextInstructionIndex()
                return
            }
            generate(thenBranch)
            conditionToElseInstruction.targetIndex = nextInstructionIndex()
            if (elseBranch != null) {
                val thenEndToAfterElseInstruction = ConditionalGoToInstruction(NOT_INITIALIZED)
                emit(thenEndToAfterElseInstruction)
                val block = elseBranch.block
                val ifExpr = elseBranch.ifExpr
                when {
                    block != null -> generate(block)
                    ifExpr != null -> generate(ifExpr)
                    else -> throw IllegalStateException()
                }
                thenEndToAfterElseInstruction.targetIndex = nextInstructionIndex()
            }
        }
    }

    override fun visitIndexExpr(indexExpr: RsIndexExpr) {
        val index = indexExpr.indexExpr
        if (index != null) {
            generate(index)
        }
        generate(indexExpr.containerExpr ?: return)
    }

    override fun visitTryExpr(tryExpr: RsTryExpr) {
        val expr = tryExpr.expr
        generate(expr)
        emit(ConditionalGoToInstruction(nextInstructionIndex() + 2))
        emit(ReturnInstruction(tryExpr))
    }

    override fun visitRetExpr(returnExpr: RsRetExpr) {
        val expr = returnExpr.expr
        if (expr != null) {
            generate(expr)
        }
        emit(ReturnInstruction(returnExpr))
    }

    override fun visitBinaryExpr(binaryExpr: RsBinaryExpr) {
        //TODO complex assignment
        if (binaryExpr.isSimpleAssignment()) {
            generateAssignment(binaryExpr)
            return
        }
        generate(binaryExpr.left)
        // TODO short circuit jump
        generate(binaryExpr.right ?: return)
    }

    private fun generateAssignment(binaryExpr: RsBinaryExpr) {
        // TODO If right part is absent, probably here should still be WRITE
        val right = binaryExpr.right ?: return
        generate(right)
        val left = binaryExpr.left as? RsPathExpr ?: return // Here also may be array index access
        val ident = left.path.resolvePatIdent() ?: return
        emit(VariableWriteInstruction(ident))
    }

    override fun visitLoopExpr(loop: RsLoopExpr) {
        val block = loop.block ?: return
        val labelDecl = loop.labelDecl
        val loopBodyStartIndex = nextInstructionIndex()
        if (labelDecl != null) {
            labelStack.push(LabelDeclInfo(labelDecl, loopBodyStartIndex))
        }
        loopStartIndexStack.add(loopBodyStartIndex)
        generate(block)
        emit(GoToInstruction(loopBodyStartIndex))
        loopStartIndexStack.removeLast()
        val nextAfterLoopIndex = nextInstructionIndex()
        while (!pendingBreakInstructionIndices.isEmpty) {
            val breakInstructionIndex = pendingBreakInstructionIndices.removeLast()
            val breakInstruction = instructions[breakInstructionIndex] as GoToInstruction
            breakInstruction.targetIndex = nextAfterLoopIndex
        }
        if (labelDecl != null) {
            labelStack.pop()
        }
    }

    override fun visitWhileExpr(whileExpr: RsWhileExpr) {
        val condition = whileExpr.condition ?: return
        val block = whileExpr.block
        if (block == null) {
            generate(condition.expr)
            return
        }

        val goToCondition = GoToInstruction(NOT_INITIALIZED)
        emit(goToCondition)
        val bodyStartIndex = nextInstructionIndex()
        // TODO wrap with break, continue handling
        generate(block)
        val conditionInstructionIndex = nextInstructionIndex()
        goToCondition.targetIndex = conditionInstructionIndex
        generate(condition.expr)
        val jumpToBody = ConditionalGoToInstruction(bodyStartIndex)
        emit(jumpToBody)
    }


    // TODO match
    override fun visitMatchExpr(o: RsMatchExpr) {
        super.visitMatchExpr(o)
    }

    override fun visitBlockExpr(blockExpr: RsBlockExpr) {
        generate(blockExpr.block)
    }

    override fun visitContExpr(continueExpr: RsContExpr) {
        val label = continueExpr.label
        val labelDecl = label?.resolve() as? RsLabelDecl
        if (labelDecl != null) {
            for (labelInfo in labelStack.descendingIterator()) {
                if (labelDecl == labelInfo.labelDecl) {
                    break
                }
            }
        }
    }

    override fun visitBreakExpr(breakExpr: RsBreakExpr) {
//        val exitedExpr = breakExpr.findExitedExpr() ?: return
        // TODO handle labels
        val breakInstructionIndex = nextInstructionIndex()
        emit(GoToInstruction(NOT_INITIALIZED))
        pendingBreakInstructionIndices.add(breakInstructionIndex)
    }
}

private fun TIntArrayList.removeLast(): Int {
    return remove(size() - 1)
}

private fun RsPath.resolvePatIdent() : RsPatIdent? {
    val binding = resolve()as? RsPatBinding ?: return null
    return binding.parent as? RsPatIdent
}
