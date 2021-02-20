/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck.gatherLoans

import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.types.borrowck.*
import org.rust.lang.core.types.borrowck.MoveReason.*
import org.rust.lang.core.types.infer.Categorization.*
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TySlice

class GatherMoveContext(private val bccx: BorrowCheckContext, private val moveData: MoveData) {
    data class GatherMoveInfo(
        val element: RsElement,
        val kind: MoveKind,
        val cmt: Cmt,
        val movePlace: MovePlace? = null
    )

    fun gatherDeclaration(variable: RsElement, variableType: Ty) {
        val loanPath = LoanPath(LoanPathKind.Var(variable), variableType, variable)
        moveData.addMove(loanPath, variable, MoveKind.Declared)
    }

    fun gatherMoveFromExpr(element: RsElement, cmt: Cmt, moveReason: MoveReason) {
        val kind = when (moveReason) {
            DirectRefMove, PatBindingMove -> MoveKind.MoveExpr
            CaptureMove -> MoveKind.Captured
        }

        val moveInfo = GatherMoveInfo(element, kind, cmt)
        gatherMove(moveInfo)
    }

    fun gatherMoveFromPat(movePat: RsPat, cmt: Cmt) {
        val patMovePlace = (movePat as? RsPatIdent)?.let { MovePlace(movePat.patBinding) }
        val moveInfo = GatherMoveInfo(movePat, MoveKind.MovePat, cmt, patMovePlace)
        gatherMove(moveInfo)
    }

    private fun gatherMove(moveInfo: GatherMoveInfo) {
        val move = getIllegalMoveOrigin(moveInfo.cmt)
        if (move != null) {
            bccx.reportMoveError(move, moveInfo.movePlace)
        } else {
            val loanPath = LoanPath.computeFor(moveInfo.cmt) ?: return
            moveData.addMove(loanPath, moveInfo.element, moveInfo.kind)
        }
    }

    fun gatherAssignment(loanPath: LoanPath, assign: RsElement, assignee: RsElement, mode: MutateMode) {
        moveData.addAssignment(loanPath, assign, assignee, mode)
    }

    private fun getIllegalMoveOrigin(cmt: Cmt): Cmt? {
        val category = cmt.category
        return when (category) {
            is Rvalue, is Local -> null

            is StaticItem, is Deref -> cmt

            is Interior.Field, is Interior.Pattern -> {
                val base = (category as Interior).cmt
                when (base.ty) {
                    is TyAdt -> if (base.ty.item.hasDestructor) cmt else getIllegalMoveOrigin(base)
                    is TySlice -> cmt
                    else -> getIllegalMoveOrigin(base)
                }
            }

            is Interior.Index -> cmt

            is Downcast -> {
                val base = category.cmt
                when (base.ty) {
                    is TyAdt -> if (base.ty.item.hasDestructor) cmt else getIllegalMoveOrigin(base)
                    is TySlice -> cmt
                    else -> getIllegalMoveOrigin(base)
                }
            }

            null -> null
        }
    }
}

// TODO: use ImplLookup
val RsStructOrEnumItemElement.hasDestructor: Boolean get() = false

val Ty.isAdtWithDestructor: Boolean get() = this is TyAdt && this.item.hasDestructor
