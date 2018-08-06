package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsPatWild
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict

class AddMatchWildcardPatternIntention : RsElementBaseIntentionAction<AddMatchWildcardPatternIntention.Context>() {
    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null

        return checkWildcard(matchExpr)
    }

    private fun checkWildcard(matchExpr: RsMatchExpr): Context? {
        val arms = matchExpr.matchBody?.matchArmList ?: return null
        arms.forEach { arm ->
            if (arm.patList.find { it is RsPatWild } != null) return null
        }
        return Context(matchExpr)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr) = ctx
        val match = buildString {
            append("match ")
            append(matchExpr.expr?.text)
            append(" {\n")
            append(matchExpr.matchBody?.matchArmList?.joinToString { it.text + "\n" })
            append(" _ => {},\n};")
        }
        val matchWithWildcard = RsPsiFactory(project).createExpression(match) as RsMatchExpr
        matchExpr.replace(matchWithWildcard)
    }

    override fun getText() = "Add wildcard expr"

    override fun getFamilyName() = text

    data class Context(val matchExpr: RsMatchExpr)

}
