package org.rust.ide.inspections.checkMatch

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.fixes.AddWildcardArmFix
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            try {
                checkUselessArm(o, holder)
                checkExhaustive(o, holder)
            } catch (todo: NotImplementedError) {}
        }

    }
}


fun checkUselessArm(match: RsMatchExpr, holder: ProblemsHolder) {
    val matrix = match.matrix
    for ((i, matchArm) in matrix.withIndex()) {
        if (i > 0) {
            val useful = isUseful(matrix.subList(0, i).map { it.patterns }, matchArm.patterns, false)
            if (!useful.isUseful()) {
                val arm = matchArm.arm
                holder.registerProblem(
                    arm,
                    "Useless match arm",
                    ProblemHighlightType.GENERIC_ERROR,
                    SubstituteTextFix.delete("Remove useless arm", match.containingFile, arm.textRange)
                )
            }
        }
    }
}

fun checkExhaustive(match: RsMatchExpr, holder: ProblemsHolder) {
    val matrix = match.matrix
    val useful = isUseful(matrix.map { it.patterns}, listOf(Pattern(TyUnknown, PatternKind.Wild)), true)
    when (useful) {
        is Usefulness.UsefulWithWitness -> {
            holder.registerProblem(
                match.match,
                "Match must be exhaustive",
                ProblemHighlightType.GENERIC_ERROR,
                AddWildcardArmFix(match, useful.witness.last().patterns.last())
            )
        }
    }
}

// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
fun isUseful(matrix: List<List<Pattern>?>, v: List<Pattern>, withWitness: Boolean): Usefulness {
    if (v.isEmpty()) {
        return if (matrix.height == 0) {
            if (withWitness) Usefulness.UsefulWithWitness(listOf(Witness(mutableListOf())))
            else Usefulness.Useful
        } else {
            Usefulness.Useless
        }
    }

    val type = matrix.mapNotNull { it?.get(0)?.ty }.find { it !== TyUnknown } ?: v[0].ty

    val constructors = v[0].constructors

    return if (constructors != null) {
        constructors.map {
            isUsefulS(matrix, v, it, type, withWitness)
        }.find { it.isUseful() } ?: Usefulness.Useless
    } else {
        val usedConstructors = matrix.flatMap { it?.get(0)?.constructors ?: emptyList() }


        val allConstructors = allConstructors(type)

        val missingConstructor = allConstructors.filter { !usedConstructors.contains(it) }


        val isPrivatelyEmpty = allConstructors.isEmpty()
        val isDeclaredNonexhaustive = (type as? TyAdt)?.isMarkedNonExhaustive ?: false

        val isNonExhaustive = isPrivatelyEmpty || isDeclaredNonexhaustive

        return if (missingConstructor.isEmpty() && !isNonExhaustive) {
            allConstructors.map {
                isUsefulS(matrix, v, it, type, withWitness)
            }.find {
                it.isUseful()
            } ?: Usefulness.Useless
        } else {
            val newMatrix = matrix.mapNotNull {
                val kind = it?.get(0)?.kind
                if (kind === PatternKind.Wild || kind is PatternKind.Binding) {
                    it.subList(1, it.size)
                } else {
                    null
                }
            }
            val res = isUseful(newMatrix, v.subList(1, v.size), withWitness)
            when (res) {
                is Usefulness.UsefulWithWitness -> {
                    val newWitness = if (isNonExhaustive || usedConstructors.isEmpty()) {
                        res.witness.map { witness ->
                            witness.patterns.add(Pattern(type, PatternKind.Wild))
                            witness
                        }
                    } else {
                        res.witness.flatMap { witness ->
                            missingConstructor.map { ctor ->
                                witness.pushWildCtor(ctor, type)
                                witness
                            }
                        }
                    }
                    Usefulness.UsefulWithWitness(newWitness)
                }
                else -> res
            }
        }
    }
}

fun isUsefulS(matrix: List<List<Pattern>?>, v: List<Pattern>, constructor: Constructor, type: Ty, withWitness: Boolean): Usefulness {
    val newMatrix = matrix.mapNotNull { specializeRow(it, constructor, type) }

    val newV = specializeRow(v, constructor, type)
    return when (newV) {
        null -> Usefulness.Useless
        else -> {
            val useful = isUseful(newMatrix, newV, withWitness)
            when (useful) {
                is Usefulness.UsefulWithWitness -> useful.apply { expandWithConstructor(constructor, type) }
                else -> useful
            }
        }
    }
}

fun specializeRow(row: List<Pattern>?, constructor: Constructor, type: Ty): List<Pattern>? {
    row ?: return null

    val wildPatterns = MutableList(constructor.arity(type)) {
        Pattern(TyUnknown, PatternKind.Wild)
    }

    val pat = row[0]
    val kind = pat.kind
    val head: List<Pattern>? = when (kind) {
        is PatternKind.Variant -> {
            if (constructor == pat.constructors?.first()) {
                wildPatterns.fillWithSubPatterns(kind.subpatterns)
                wildPatterns
            } else {
                null
            }
        }
        is PatternKind.Leaf -> {
            wildPatterns.fillWithSubPatterns(kind.subpatterns)
            wildPatterns
        }
        is PatternKind.Deref -> listOf(kind.subpattern)
        is PatternKind.Constant -> when (constructor) {
            is Constructor.Slice -> TODO("Specialize row for slice pattern")
            else ->
                if (constructor.coveredByRange(kind.value, kind.value, true)) listOf<Pattern>()
                else null

        }

        is PatternKind.Range -> {
            if (constructor.coveredByRange(kind.lc, kind.rc, kind.included)) listOf()
            else null
        }
        is PatternKind.Slice, is PatternKind.Array -> {
            var prefix: List<Pattern> = listOf()
            var slice: Pattern? = null
            var suffix: List<Pattern> = listOf()
            (kind as PatternKind.SliceField).let {
                prefix = it.prefix
                slice = it.slice
                suffix = it.suffix
            }

            when (constructor) {
                is Constructor.Slice -> {
                    val patternLength = prefix.size + suffix.size
                    val sliceCount = wildPatterns.size - patternLength
                    if(sliceCount == 0 || slice != null) {
                        prefix + wildPatterns.subList(prefix.size - 1, sliceCount + prefix.size - 1) + suffix
                    } else null
                }
                is Constructor.ConstantValue -> {
                    //slice_pat_covered_by_constructor
                    TODO("Check pattern slice and constructor value")
                }
                else -> error("Bag. Wrong constructor")
            }

        }
        PatternKind.Wild, is PatternKind.Binding -> wildPatterns
    }
    return head?.plus(row.subList(1, row.size))
}
