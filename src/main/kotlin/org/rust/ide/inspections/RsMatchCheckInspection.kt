package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
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
    val seen = mutableListOf<List<RsPat>>()
    var catchAll = false
    arms.forEachIndexed { index, pair ->
        isUseful(seen, pair.first)
    }
}


// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
fun isUseful(matrix: List<List<RsPat>>, v: List<RsPat>): Boolean {
    println("<top>.isUseful(matrix = $matrix, v = $v)")


    // Base case if we are pattern-matching on ()
    if (v.isEmpty()) {
        // return value is based on whether our matrix has a row or not
        return matrix.isEmpty()
    }


    //// Induction

    // Check matrix and pattern length
    if (matrix.all { it.size == v.size }) throw Exception("matrix width != pattern width")

    // Получим тип
    val ty = matrix.map { it[0].type }

    val constr = v[0].constructors


    // If constr is Constructor
    when {

        // Pattern v is or-pattern
        v.size > 1 -> {
        }
        // Pattern v[0] is a constructed pattern (v[0] = c(r0, r1, ..., ra) )
        !constr.isEmpty() -> {
            // Get specialize matrix S(c, P). Width = a + n - 1

        }
        // Pattern v[0] is wildcard
        constr.isEmpty() -> {
        }
    }



    return true
}

fun specialize(constr: Constructor, matrix: List<List<RsPat>>): List<List<RsPat>> {
    println("<top>.specialize(constr = $constr, matrix = $matrix)")
    val newMatrix = mutableListOf<List<RsPat>>()
    for (row in matrix) {
        val rowConstrs = row[0].constructors
        when {
            // Wildcard
            rowConstrs.isEmpty() -> {

            }
            // Or-pattern
            rowConstrs.size > 1 -> {

            }
            // p[0] == c
            rowConstrs[0] == constr -> {
                val newRow = mutableListOf<RsPat>()
                when (constr) {
                    is Single -> newMatrix.add(row.slice(1..row.size))
                    is Variant -> TODO()
                    is ConstantValue -> TODO()
                    is ConstantRange -> TODO()
                    is Slice -> TODO()
                }
            }
            // p[0] != c
            rowConstrs[0] != constr -> {
            }
        }
    }
    return emptyList()
}


sealed class Constructor

/// The constructor of all patterns that don't vary by constructor,
/// e.g. struct patterns and fixed-length arrays.
//Single
class Single : Constructor()

/// Enum variants.
// Variant(DefId)
data class Variant(val variant: RsEnumVariant) : Constructor()

/// Literal values.
//ConstantValue(&'tcx ty::Const<'tcx>),
data class ConstantValue(val expr: RsExpr) : Constructor()

/// Ranges of literal values (`2...5` and `2..5`).
//ConstantRange(&'tcx ty::Const<'tcx>, &'tcx ty::Const<'tcx>, RangeEnd),
data class ConstantRange(val start: RsPatConst, val end: RsPatConst, val includeEnd: Boolean = false) : Constructor()

/// Array patterns of length n.
//Slice(u64),
class Slice : Constructor()

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
            is RsPatStruct -> path.getConstructors()
            is RsPatTupleStruct -> path.getConstructors()
            is RsPatRef -> listOf(Single())
            is RsPatUniq -> listOf(Single()) // Не понимаю что это за шаблоны такие. Кажется `box a`
            is RsPatConst -> listOf(ConstantValue(this.expr)) // Надо бы достать значение. Ну только если нужно
            is RsPatRange -> listOf(ConstantRange(patConstList[0], patConstList[1], dotdotdot == null)) // TODO ..=
            is RsPatTup -> listOf(Single()) // Вместе с структурой и енумом?
            is RsPatIdent, is RsPatWild -> emptyList()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> emptyList()
        }
    }

fun RsPath.getConstructors(): List<Constructor> {
    val item = reference.resolve()
    return if (item is RsEnumVariant) listOf(Variant(item))
    else listOf(Single())
}

val RsPat.type: Ty
    get() = when (this) {
        is RsPatTupleStruct -> TODO()
        is RsPatMacro -> TODO()
        is RsPatTup -> TODO()
        is RsPatConst -> TODO()
        is RsPatUniq -> TODO()
        is RsPatSlice -> TODO()
        is RsPatRange -> TODO()
        is RsPatRef -> TODO()
        is RsPatIdent -> TODO()
        is RsPatWild -> TODO()
        is RsPatStruct -> TODO()
        else -> TyUnknown/*ignore*/
    }


