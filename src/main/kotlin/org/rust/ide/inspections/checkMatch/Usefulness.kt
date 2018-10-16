package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.types.ty.*

class Witness(val patterns: MutableList<Pattern>) {
    override fun toString() = patterns.toString()

    fun pushWildCtor(constructor: Constructor, type: Ty) {
        println("Witness.pushWildCtor(constructor = $constructor, type = $type)")
        val subPatterns = constructor.subTys(type)
        println("Witness.pushWildCtor subPatterns=$subPatterns")
        for (ty in subPatterns) {
            patterns.add(Pattern(ty, PatternKind.Wild, null))
        }
        applyConstructors(constructor, type)
    }

    fun applyConstructors(constructor: Constructor, type: Ty) {
        println("Witness.applyConstructors(constructor = $constructor, type = $type)")
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
        println("Witness.applyConstructors pat=$pat")
        patterns.add(Pattern(type, pat, null))
        println("Witness.applyConstructors patterns=$patterns")
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
