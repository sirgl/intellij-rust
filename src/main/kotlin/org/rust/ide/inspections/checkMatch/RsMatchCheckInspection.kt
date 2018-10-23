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

            println("**************************************** NEW ****************************************")

            checkExhaustive(o, holder)
            checkUselessArm(o, holder)
        }

    }
}

// ************************************************** ALGORITHM **************************************************

fun checkUselessArm(match: RsMatchExpr, holder: ProblemsHolder) {
    println("<top>.checkUselessArm(match = $match, holder = $holder)")
    val matrix = match.matrix
    for ((i, matchArm) in matrix.withIndex()) {
        if (i > 0) {
            val useful = isUseful(matrix.subList(0, i).map { it.patterns }, matchArm.patterns, false)
            if (!useful.isUseful()) {
//                val matchArm = match.matchBody?.matchArmList?.get(i) ?: continue
                val arm = matchArm.arm ?: continue
                println("<top>.checkUselessArm arm ${arm.text} useless")
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
    println("<top>.checkExhaustive(match = $match, holder = $holder)")
    val matrix = match.matrix
    val useful = isUseful(matrix.map { it.patterns}, listOf(Pattern(TyUnknown, PatternKind.Wild)), true)
    when (useful) {
        is Usefulness.UsefulWithWitness -> {
            println("<top>.checkExhaustive useful=${useful.witness}")
            holder.registerProblem(
                match.match,
                "Match must be exhaustive",
                ProblemHighlightType.GENERIC_ERROR,
                AddWildcardArmFix(match, useful.witness.last().patterns.last())
            )
        }
        Usefulness.Useless -> println("<top>.checkExhaustive useful=$useful")
    }
}

// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
fun isUseful(matrix: List<List<Pattern>?>, v: List<Pattern>, withWitness: Boolean): Usefulness {
    println("<top>.isUseful(matrix = $matrix, v = $v)")

    //// Base

    // Base case if we are pattern-matching on ()
    if (v.isEmpty()) {
        return if (matrix.height == 0) {
            if (withWitness) Usefulness.UsefulWithWitness(listOf(Witness(mutableListOf())))
            else Usefulness.Useful
        } else {
            Usefulness.Useless
        }
    }

    //// Induction
    val type = matrix.mapNotNull { it?.get(0)?.ty }.find { it !== TyUnknown } ?: v[0].ty

    val constructors = v[0].constructors

    println("<top>.isUseful expand first col: expanding ${v[0]} type=$type")
    return if (constructors != null) {
        println("<top>.isUseful expanding constructors $constructors")
        constructors.map {
            isUsefulS(matrix, v, it, type, withWitness)
        }.find { it.isUseful() } ?: Usefulness.Useless
    } else {
        println("<top>.isUseful expanding wildcard")
        val usedConstructors = matrix.flatMap { it?.get(0)?.constructors ?: emptyList() }
        println("<top>.isUseful used constructors $usedConstructors")


        val allConstructors = allConstructors(type)
        println("<top>.isUseful allConstructors $allConstructors")

        val missingConstructor = allConstructors.filter { !usedConstructors.contains(it) }


        val isPrivatelyEmpty = allConstructors.isEmpty()
        val isDeclaredNonexhaustive = (type as? TyAdt)?.isMarkedNonExhaustive ?: false
        println("<top>.isUseful missing constructors $missingConstructor\tis privately empty $isPrivatelyEmpty, " +
            "is declared nonexhaustive $isDeclaredNonexhaustive")

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
    println("<top>.isUsefulS(matrix = $matrix, v = $v, constructor = $constructor)")

    val newMatrix = matrix.mapNotNull { specializeRow(it, constructor, type) }
    println("<top>.isUsefulS newMatrix=$newMatrix")

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
    println("<top>.specializeRow(row = $row, constructor = $constructor)")
    row ?: return null // FIXME не уверен в надобности

    val wildPatterns = mutableListOf<Pattern>()
    repeat(constructor.arity(type)) {
        wildPatterns.add(Pattern(TyUnknown, PatternKind.Wild))
    }

    val pat = row[0]
    val kind = pat.kind
    val head: List<Pattern>? = when (kind) {
        is PatternKind.Variant -> {
            if (constructor == pat.constructors?.first()) {
//                patternsForVariant(kind.subpatterns, constructor.arity(type))
                wildPatterns.fillWithSubPatterns(kind.subpatterns)
                wildPatterns
            } else {
                null
            }
        }
        is PatternKind.Leaf -> {
            /**
             * Вот здесь непонятно. В соотвествии с алгоритом необходимо проверить равенство конструкторов
             * (как в случае с вариантом). Но в компиляторе они так не делают. Очень странно. И непонятно.
             */
//            patternsForVariant(kind.subpatterns, constructor.arity(type))
            wildPatterns.fillWithSubPatterns(kind.subpatterns)
            wildPatterns
        }
        is PatternKind.Deref -> listOf(kind.subpattern)
        is PatternKind.Constant -> when (constructor) {
            is Constructor.Slice -> TODO()
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
            (kind as? PatternKind.Slice)?.let {
                prefix = it.prefix
                slice = it.slice
                suffix = it.suffix
            } ?: (kind as? PatternKind.Array)?.let {
                prefix = it.prefix
                slice = it.slice
                suffix = it.suffix
            } ?: error("")

            when (constructor) {
                is Constructor.Slice -> {
                    val patternLength = prefix.size + suffix.size
                    val sliceCount = wildPatterns.size - patternLength
                    if(sliceCount == 0 || slice != null) {
                       /* prefix.iter().chain(
                            wild_patterns.iter()
                                .map(|p| *p)
                            .skip(prefix.len())
                            .take(slice_count)
                            .chain(suffix.iter())
                        ).collect())*/
                        prefix.plus(wildPatterns.subList(prefix.size - 1, sliceCount + prefix.size - 1)).plus(suffix)
                    } else null
                }
                is Constructor.ConstantValue -> {
                    /*match slice_pat_covered_by_constructor(
                        cx.tcx, pat.span, constructor, prefix, slice, suffix
                    ) {
                        Ok(true) => Some(vec![]),
                        Ok(false) => None,
                        Err(ErrorReported) => None
                    }*/
                    TODO()
                }
                else -> error("")
            }

        }
        PatternKind.Wild, is PatternKind.Binding -> wildPatterns
    }
    println("<top>.specializeRow head=$head")
    return head?.plus(row.subList(1, row.size))
}

fun sliceCoveredByConstructor(constructor: Constructor, prefix: List<Pattern>, slice: Pattern?, suffix: List<Pattern>): Boolean {
    /*let data: &[u8] = match *ctor {
        ConstantValue(const_val) => {
            let val = match const_val.val {
                ConstValue::Unevaluated(..) |
                ConstValue::ByRef(..) => bug!("unexpected ConstValue: {:?}", const_val),
                ConstValue::Scalar(val) | ConstValue::ScalarPair(val, _) => val,
            };
            if let Ok(ptr) = val.to_ptr() {
                let is_array_ptr = const_val.ty
                    .builtin_deref(true)
                    .and_then(|t| t.ty.builtin_index())
                    .map_or(false, |t| t == tcx.types.u8);
                assert!(is_array_ptr);
                tcx.alloc_map.lock().unwrap_memory(ptr.alloc_id).bytes.as_ref()
            } else {
                bug!("unexpected non-ptr ConstantValue")
            }
        }
        _ => bug!()
    };

    let pat_len = prefix.len() + suffix.len();
    if data.len() < pat_len || (slice.is_none() && data.len() > pat_len) {
        return Ok(false);
    }

    for (ch, pat) in
        data[..prefix.len()].iter().zip(prefix).chain(
            data[data.len()-suffix.len()..].iter().zip(suffix))
    {
        match pat.kind {
            box PatternKind::Constant { value } => {
                let b = value.unwrap_bits(tcx, ty::ParamEnv::empty().and(pat.ty));
                assert_eq!(b as u8 as u128, b);
                if b as u8 != *ch {
                    return Ok(false);
                }
            }
            _ => {}
        }
    }
    Ok(true)
    */

    val patternLength = prefix.size + suffix.size
    return true

}

fun getLeafOrVariant(item: RsElement, subpatterns: List<FieldPattern>): PatternKind {
    return when (item) {
        is RsEnumVariant -> PatternKind.Variant(TyAdt.valueOf(item.parentEnum), item.index ?: error("Can't get index"), subpatterns)
        is RsStructItem -> PatternKind.Leaf(subpatterns)
        else -> error("Impossible case $item")
    }
}

fun MutableList<Pattern>.fillWithSubPatterns(subpatterns: List<FieldPattern>) {
    for (subpattern in subpatterns) {
        this[subpattern.first] = subpattern.second
    }
}
