package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt

sealed class PatternKind {
    object Wild : PatternKind()

    /// x, ref x, x @ P, etc
    data class Binding(val ty: Ty) : PatternKind()


    /// Foo(...) or Foo{...} or Foo, where `Foo` is a variant name from an adt with >1 variants
    data class Variant(val ty: TyAdt, val variantIndex: Int, val subpatterns: List<FieldPattern>) : PatternKind()

    /// (...), Foo(...), Foo{...}, or Foo, where `Foo` is a variant name from an adt with 1 variant
    data class Leaf(val subpatterns: List<FieldPattern>) : PatternKind()

    /// box P, &P, &mut P, etc
    data class Deref(val subpattern: Pattern) : PatternKind()

    data class Constant(val value: org.rust.ide.inspections.checkMatch.Constant) : PatternKind()

    data class Range(val lc: org.rust.ide.inspections.checkMatch.Constant, val rc: org.rust.ide.inspections.checkMatch.Constant, val included: Boolean) : PatternKind()


    interface SliceField{
        val prefix: List<Pattern>
        val slice: Pattern?
        val suffix: List<Pattern>
    }
    /// matches against a slice, checking the length and extracting elements.
    /// irrefutable when there is a slice pattern and both `prefix` and `suffix` are empty.
    /// e.g. `&[ref xs..]`.
    data class Slice(override val prefix: List<Pattern>,
                     override val slice: Pattern?,
                     override val suffix: List<Pattern>)
        : PatternKind(), SliceField

    /// fixed match against an array, irrefutable
    data class Array(override val prefix: List<Pattern>,
                     override val slice: Pattern?,
                     override val suffix: List<Pattern>)
        : PatternKind(), SliceField
}

