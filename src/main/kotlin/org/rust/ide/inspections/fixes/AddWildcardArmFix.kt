package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsPsiFactory

class AddWildcardArmFix(match: RsMatchExpr) : LocalQuickFixOnPsiElement(match) {
    private val newMatchText = """
                            match ${match.expr?.text} {
                                ${match.matchBody?.matchArmList?.joinToString(separator = "\n") { it.text }}
                                _ => {}
                            }
                        """

    override fun getFamilyName() = "ADD"

    override fun getText() = "ADD"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val match = RsPsiFactory(project).createExpression(newMatchText) as RsMatchExpr
        startElement.replace(match).also {
            CodeStyleManager.getInstance(project).reformat(it)
        }
    }

}
