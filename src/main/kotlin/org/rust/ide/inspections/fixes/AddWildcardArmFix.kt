package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.rust.ide.inspections.checkMatch.Pattern
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsPsiFactory

class AddWildcardArmFix(match: RsMatchExpr, val pattern: Pattern) : LocalQuickFixOnPsiElement(match) {
    private val newMatchText = """
                            match ${match.expr?.text} {
                                ${match.matchBody?.matchArmList?.joinToString(separator = "\n") { it.text }}
                                $pattern => {}
                            }
                        """

    override fun getFamilyName() = "Add $pattern pattern"

    override fun getText() = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val match = RsPsiFactory(project).createExpression(newMatchText) as RsMatchExpr
        startElement.replace(match).also {
            CodeStyleManager.getInstance(project).reformat(it)
        }
    }

}
