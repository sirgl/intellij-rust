/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RsReference

interface RsWeakReferenceElement : RsElement {

    val referenceNameElement: PsiElement?

    val referenceName: String?

    override fun getReference(): RsReference?
}

interface RsReferenceElement : RsWeakReferenceElement {

    override val referenceNameElement: PsiElement

    override val referenceName: String

    override fun getReference(): RsReference
}
