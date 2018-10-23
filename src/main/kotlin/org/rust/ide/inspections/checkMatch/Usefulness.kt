package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.types.ty.*

class Witness(val patterns: MutableList<Pattern>) {
    override fun toString() = patterns.toString()

    fun pushWildCtor(constructor: Constructor, type: Ty) {
        val subPatterns = constructor.subTys(type)
        for (ty in subPatterns) {
            patterns.add(Pattern(ty, PatternKind.Wild))
        }
        applyConstructors(constructor, type)
    }

    fun applyConstructors(constructor: Constructor, type: Ty) {
        val arity = constructor.arity(type)
        val len = patterns.size
        val pats = patterns.subList(len - arity, len).reversed()
        val pat = when (type) {
            is TyAdt, is TyTuple -> {
                val newPats = pats.mapIndexed { index, pattern ->
                    FieldPattern(index, pattern)
                }
                if (type is TyAdt) {
                    if (type.item is RsEnumItem) {
                        PatternKind.Variant(
                            type,
                            (constructor as Constructor.Variant).index,
                            newPats
                        )
                    } else {
                        PatternKind.Leaf(newPats)
                    }
                } else {
                    PatternKind.Leaf(newPats)
                }
            }
            is TyReference -> {
                PatternKind.Deref(pats[0])
            }
            is TySlice, is TyArray -> TODO()
            else -> {
                when (constructor) {
                    is Constructor.ConstantValue -> PatternKind.Constant(constructor.value)
                    else -> PatternKind.Wild
                }
            }
        }
        patterns.add(Pattern(type, pat))
    }
}

sealed class Usefulness {
    class UsefulWithWitness(val witness: List<Witness>) : Usefulness() {
        fun expandWithConstructor(constructor: Constructor, type: Ty) {
            witness.forEach {
                it.applyConstructors(constructor, type)
            }
            Usefulness.UsefulWithWitness(witness)
        }
    }

    object Useful : Usefulness()
    object Useless : Usefulness()

    fun isUseful(): Boolean = when (this) {
        Usefulness.Useless -> false
        else -> true
    }
}
