/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatField

val RsPatField.kind: RsPatFieldKind
    get() = patBinding?.let { RsPatFieldKind.Shorthand(it) } ?: RsPatFieldKind.Full(identifier!!, pat!!)

// PatField ::= identifier ':' Pat | box? PatBinding
sealed class RsPatFieldKind {
    /**
     * struct S { a: i32 }
     * let S { a : ref b } = ...
     *         ~~~~~~~~~
     */
    data class Full(val ident: PsiElement, val pat: RsPat): RsPatFieldKind()
    /**
     * struct S { a: i32 }
     * let S { ref a } = ...
     *         ~~~~~
     */
    data class Shorthand(val binding: RsPatBinding): RsPatFieldKind()
}

val RsPatFieldKind.fieldName: String
    get() = when (this) {
        is RsPatFieldKind.Full -> ident.text
        is RsPatFieldKind.Shorthand -> binding.identifier.text
    }
