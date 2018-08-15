package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val ty = o.expr?.type ?: return
            val arms = o.matchBody?.matchArmList?.map {
                it.patList to it.matchArmGuard
            } ?: emptyList()
            checkArms(arms, holder)
        }
    }
}


fun checkArms(arms: List<Pair<List<RsPat>, RsMatchArmGuard?>>, holder: ProblemsHolder) {
    println("<top>.checkArms(arms = $arms, holder = $holder)")
    val seen = mutableListOf<List<RsPat>>()
    var catchAll = false
    arms.forEachIndexed { index, pair ->
        println("${pair.first} isUseful = ${isUseful(seen, pair.first)}")
        seen.add(pair.first)
    }
}


// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
fun isUseful(matrix: List<List<RsPat>>, v: List<RsPat>): Boolean {
    println("<top>.isUseful(matrix = $matrix, v = $v)")

    //// Base

    // Base case if we are pattern-matching on ()
    if (matrix.width == 0) {
        return matrix.height == 0
    }

    //// Induction

    val constr = v[0].constructors

    // If constr is Constructor
    when {

        // Pattern v is or-pattern
        v.size > 1 -> {
        }
        // Pattern v[0] is a constructed pattern (v[0] = c(r0, r1, ..., ra) )
        !constr.isEmpty() -> {
            // Get specializeMatrix matrix S(c, P). Width = a + n - 1
            TODO()
        }
        // Pattern v[0] is wildcard
        constr.isEmpty() -> {
        }
    }



    return true
}

fun specializeMatrix(constr: Constructor, matrix: List<List<RsPat>>): List<List<RsPat>> {
    println("<top>.specializeMatrix(constr = $constr, matrix = $matrix)")
    val newMatrix = mutableListOf<List<RsPat>>()
    for (row in matrix) {
        TODO("Get specialized row and add in new matrix")
    }
    return emptyList()
}

fun specializeRow(row: List<RsPat>, constructor: Constructor): List<RsPat> {
    println("<top>.specializeRow(row = $row, constructor = $constructor)")
    val rowConstrs = row[0].constructors
    when {
        // Wildcard
        rowConstrs.isEmpty() -> {
            TODO("first pattern is wildcard")
        }
        // Or-pattern
        rowConstrs.size > 1 -> {
            TODO("first pattern is or-pattern")
        }
        // p[0] == c
        rowConstrs[0] == constructor -> {
            when (constructor) {
                is ConstantValue -> {
                    return row.slice(1..row.size)
                }
                is Variant -> {
                    val variant = constructor.variant
                    return when {
                        variant.blockFields != null -> {
                            val pat = constructor.pat as RsPatStruct
                            val newRow = pat.patFieldList.mapNotNull { it.pat }.plus(row.slice(1..row.size))
                            println("variant has blockFields => newRow $newRow")
                            newRow
                        }
                        variant.tupleFields != null -> {
                            val pat = constructor.pat as RsPatTupleStruct
                            val newRow = pat.patList.plus(row.slice(1..row.size))
                            println("variant has tupleFields => newRow ${newRow}")
                            newRow
                        }
                        else -> {
                            val newRow = row.slice(1..row.size)
                            println("variant without field => newRow $newRow")
                            newRow
                        }
                    }
                }
                is Single -> TODO()
                is ConstantRange -> TODO()
                is Slice -> TODO()
            }
        }
        // p[0] != c
        rowConstrs[0] != constructor -> {
            TODO("first pattern is different constructor")
        }
    }
    return emptyList()
}

sealed class Constructor(open val pat: RsPat)

/// The constructor of all patterns that don't vary by constructor,
/// e.g. struct patterns and fixed-length arrays.
//Single
data class Single(override val pat: RsPat) : Constructor(pat)

/// Enum variants.
// Variant(DefId)
data class Variant(override val pat: RsPat, val variant: RsEnumVariant) : Constructor(pat)

/// Literal values.
//ConstantValue(&'tcx ty::Const<'tcx>),
data class ConstantValue(override val pat: RsPat, val expr: RsExpr) : Constructor(pat)

/// Ranges of literal values (`2...5` and `2..5`).
//ConstantRange(&'tcx ty::Const<'tcx>, &'tcx ty::Const<'tcx>, RangeEnd),
data class ConstantRange(override val pat: RsPat, val start: RsPatConst, val end: RsPatConst, val includeEnd: Boolean = false) : Constructor(pat)


/// Array patterns of length n.
//Slice(u64),
data class Slice(override val pat: RsPat) : Constructor(pat)

/*/// Determines the constructors that the given pattern can be specialized to.
///
/// In most cases, there's only one constructor that a specific pattern
/// represents, such as a specific enum variant or a specific literal value.
/// Slice patterns, however, can match slices of different lengths. For instance,
/// `[a, b, ..tail]` can match a slice of length 2, 3, 4 and so on.
///
/// Returns None in case of a catch-all, which can't be specialized.
fn pat_constructors<'tcx>(cx: &mut MatchCheckCtxt,
    pat: &Pattern<'tcx>,
    pcx: PatternContext)
        -> Option<Vec<Constructor<'tcx>>> {
    match *pat.kind {
        PatternKind::Binding { .. } | PatternKind::Wild => ++++++
              None,
        PatternKind::Leaf { .. } | PatternKind::Deref { .. } =>
              Some(vec![Single]),
        PatternKind::Variant { adt_def, variant_index, .. } =>
              Some(vec![Variant(adt_def.variants[variant_index].did)]),
        PatternKind::Constant { value } =>
              Some(vec![ConstantValue(value)]),
        PatternKind::Range { lo, hi, end } =>
              Some(vec![ConstantRange(lo, hi, end)]),
        PatternKind::Array { .. } => match pcx.ty.sty {
              ty::TyArray(_, length) => Some(vec![Slice(length.unwrap_usize(cx.tcx))]),
        _ => span_bug!(pat.span, "bad ty {:?} for array pattern", pcx.ty)
        },
        PatternKind::Slice { ref prefix, ref slice, ref suffix } => {
              let pat_len = prefix.len() as u64 + suffix.len() as u64;
              if slice.is_some() {
                  Some((pat_len..pcx.max_slice_length+1).map(Slice).collect())
              } else {
                  Some(vec![Slice(pat_len)])
              }
        }
    }
}*/

val RsPat.constructors: List<Constructor>
    get() {
        return when (this) {
            is RsPatStruct -> path.singleOrVariant(this)
            is RsPatTupleStruct -> path.singleOrVariant(this)
            is RsPatRef -> listOf(Single(this))
            is RsPatUniq -> listOf(Single(this)) // Не понимаю что это за шаблоны такие. Кажется `box a`
            is RsPatConst -> listOf(ConstantValue(this, this.expr)) // Надо бы достать значение. Ну только если нужно
            is RsPatRange -> listOf(ConstantRange(this, patConstList[0], patConstList[1], dotdotdot == null)) // TODO ..=
            is RsPatTup -> listOf(Single(this)) // Вместе с структурой и енумом?
            is RsPatIdent, is RsPatWild -> emptyList()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> emptyList()
        }
    }

fun RsPath.singleOrVariant(pat: RsPat): List<Constructor> {
    val item = reference.resolve()
    return if (item is RsEnumVariant) listOf(Variant(pat, item))
    else listOf(Single(pat))
}


val List<List<*>>.width: Int
    get() {
        return this.maxWith(Comparator { p0, p1 ->
            if (p0.size > p1.size) 1
            else -1
        })?.size ?: 0
    }

val List<List<*>>.height: Int
    get() = this.size
