package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            o.expr?.type ?: return
            val matrix = o.matchBody?.matchArmList?.map { arm ->
                arm.patList.map { lowerPattern(it) } to arm.matchArmGuard
            } ?: emptyList()
            matrix.forEach {
                println(it)
            }
        }

    }
}


/*fun checkArms(arms: List<Pair<List<RsPat>, RsMatchArmGuard?>>, holder: ProblemsHolder) {
    println("<top>.checkArms(arms = $arms, holder = $holder)")
    val seen = mutableListOf<List<RsPat>>()
    var catchAll = false
    arms.forEachIndexed { index, pair ->
        println("${pair.first} isUseful = ${isUseful(seen, pair.first)}")
        seen.add(pair.first)
    }
}*/

// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
/*fun isUseful(matrix: List<List<RsPat>>, v: List<RsPat>): Boolean {
    println("<top>.isUseful(matrix = $matrix, v = $v)")
    val matrix = matrix

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
            return isUseful(specializeMatrix(constr.first(), matrix), specializeRow(constr.first(), v).first())
        }
        // Pattern v[0] is wildcard
        constr.isEmpty() -> {
        }
    }



    return true
}*/

fun specializeMatrix(constr: Constructor, matrix: List<List<RsPat>>): List<List<RsPat>> {
    println("<top>.specializeMatrix(constr = $constr, matrix = $matrix)")
    val newMatrix = mutableListOf<List<RsPat>>()
    for (row in matrix) {
        specializeRow(constr, row).forEach { newMatrix.add(it) }
//        TODO("Get specialized row and add in new matrix")
    }
    return newMatrix
}

fun specializeRow(constructor: Constructor, row: List<Pattern>): List<List<RsPat>> {
    println("<top>.specializeRow(constructor = $constructor, row = $row)")

    val pat = row[0]
    val kind = pat.kind
    val head: List<Pattern> = when (kind) {
        is PatternKind.Leaf -> TODO()
        is PatternKind.Binding -> TODO()
        is PatternKind.Variant -> TODO()
        is PatternKind.Deref -> TODO()
        is PatternKind.Constant -> TODO()
        is PatternKind.Range -> TODO()
        is PatternKind.Slice -> TODO()
        is PatternKind.Array -> TODO()
        PatternKind.Wild -> {
            val result = mutableListOf<Pattern>()
            repeat(constructor.size) {
                result.add(Pattern(TyUnknown, PatternKind.Wild))
            }
            result
        }
    }

    return emptyList()
}

val Pattern.constructors: List<Constructor>
    get() {
        return listOf(Constructor.Single())
    }

sealed class Constructor {

    /// The constructor of all patterns that don't vary by constructor,
    /// e.g. struct patterns and fixed-length arrays.
    //Single
    data class Single(val pat: RsPat) : Constructor()

    /// Enum variants.
    // Variant(DefId)
    data class Variant(val pat: RsPat, val variant: RsEnumVariant) : Constructor()

    /// Literal values.
    //ConstantValue(&'tcx ty::Const<'tcx>),
    data class ConstantValue(val pat: RsPat, val expr: RsExpr) : Constructor()

    /// Ranges of literal values (`2...5` and `2..5`).
    //ConstantRange(&'tcx ty::Const<'tcx>, &'tcx ty::Const<'tcx>, RangeEnd),
    data class ConstantRange(val pat: RsPat, val start: RsPatConst, val end: RsPatConst, val includeEnd: Boolean = false) : Constructor()

    /// Array patterns of length n.
    //Slice(u64),
    data class Slice(val pat: RsPat) : Constructor()
}

typealias FieldPattern = Pair<Int, Pattern>

data class Pattern(val ty: Ty, val kind: PatternKind)
sealed class PatternKind {
    object Wild : PatternKind()

    /// x, ref x, x @ P, etc
    /*Binding {
        mutability: Mutability,
        name: ast::Name,
        mode: BindingMode<'tcx>,
        var: ast::NodeId,
        ty: Ty<'tcx>,
        subpattern: Option<Pattern<'tcx>>,
    }*/
    data class Binding(val ty: Ty) : PatternKind()


    /// Foo(...) or Foo{...} or Foo, where `Foo` is a variant name from an adt with >1 variants
    /*Variant {
        adt_def: &'tcx AdtDef,
        substs: &'tcx Substs<'tcx>,
        variant_index: usize,
        subpatterns: Vec<FieldPattern<'tcx>>,
    }*/
    data class Variant(val ty: TyAdt, val variantIndex: Int, val subpatterns: List<FieldPattern>) : PatternKind()

    /// (...), Foo(...), Foo{...}, or Foo, where `Foo` is a variant name from an adt with 1 variant
    /*Leaf {
        subpatterns: Vec<FieldPattern<'tcx>>,
    }*/
    data class Leaf(val subpatterns: List<FieldPattern>) : PatternKind()

    /// box P, &P, &mut P, etc
    /*Deref {
        subpattern: Pattern<'tcx>,
    }*/
    data class Deref(val subpattern: Pattern) : PatternKind()

    /*Constant {
        value: &'tcx ty::Const<'tcx>,
    }*/
    data class Constant(val value: RsExpr) : PatternKind()

    /*Range {
        lo: &'tcx ty::Const<'tcx>,
        hi: &'tcx ty::Const<'tcx>,
        end: RangeEnd,
    }*/
    data class Range(val lc: RsConstant, val rc: RsConstant, val included: Boolean) : PatternKind()

    /// matches against a slice, checking the length and extracting elements.
    /// irrefutable when there is a slice pattern and both `prefix` and `suffix` are empty.
    /// e.g. `&[ref xs..]`.
    /*Slice {
        prefix: Vec<Pattern<'tcx>>,
        slice: Option<Pattern<'tcx>>,
        suffix: Vec<Pattern<'tcx>>,
    }*/
    data class Slice(val prefix: List<Pattern>, val slice: Pattern?, val suffix: List<Pattern>) : PatternKind()

    /// fixed match against an array, irrefutable
    /*Array {
        prefix: Vec<Pattern<'tcx>>,
        slice: Option<Pattern<'tcx>>,
        suffix: Vec<Pattern<'tcx>>,
    }*/
    data class Array(val prefix: List<Pattern>, val slice: Pattern?, val suffix: List<Pattern>) : PatternKind()
}

val RsPat.type: Ty
    get() = when (this) {
        is RsPatConst -> {
            expr.type
        }
        is RsPatStruct, is RsPatTupleStruct -> {
            val path = when (this) {
                is RsPatTupleStruct -> path
                is RsPatStruct -> path
                else -> null
            }
            val tmp = path?.reference?.resolve()
            when {
                tmp is RsEnumVariant -> {
                    TyAdt.valueOf(tmp.parentEnum)
                }
                tmp is RsStructOrEnumItemElement -> {
                    TyAdt.valueOf(tmp)
                }
                else -> {
                    TyUnknown
                }
            }
        }
        is RsPatWild -> {
            TyUnknown
        }
        is RsPatIdent -> {
            patBinding.type
        }
        is RsPatTup -> {
            TyTuple(patList.map { it.type })
        }
        is RsPatRef -> TODO()
        is RsPatUniq -> TODO()
        is RsPatRange -> TODO()
        is RsPatMacro -> TODO()
        is RsPatSlice -> TODO()
        else -> TODO()
    }


fun lowerPattern(pat: RsPat): Pattern {
    println("<top>.lowerPattern(pat = $pat)")
    val kind = pat.kind
    val ty = pat.type
    return Pattern(ty, kind)
}

val RsPat.kind: PatternKind
    get() {
        println("RsPat.kind")
        return when (this) {
            is RsPatIdent -> {

                PatternKind.Binding(this.patBinding.type)
            }
            is RsPatWild -> {
                PatternKind.Wild
            }
            is RsPatTup -> {
                PatternKind.Leaf(this.patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat)
                })
            }
            is RsPatStruct -> {
                println("\tRsPatStruct")
                val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
                val subpatterns: List<FieldPattern> = patFieldList.map { patField ->
                    val pat = patField.pat
                    val binding = patField.patBinding
                    val pattern = if (pat != null) {
                        lowerPattern(pat)
                    } else {
                        binding?.type?.let { ty ->
                            Pattern(ty, PatternKind.Binding(ty))
                        } ?: error("Binding type = null")
                    }
                    getFieldIndex(item as RsFieldsOwner, patField) to pattern
                }

                getLeafOrVariant(item, subpatterns)
            }
            is RsPatTupleStruct -> {
                println("\tRsPatTupleStruct")
                val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
                val subpatterns: List<FieldPattern> = patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat) // TODO patIdent ok?
                }

                getLeafOrVariant(item, subpatterns)
            }
            is RsPatConst -> {
                PatternKind.Constant(expr)
            }

            is RsPatRef -> TODO()
            is RsPatUniq -> TODO()
            is RsPatRange -> TODO()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> TODO()
        }

    }

fun getLeafOrVariant(item: RsElement, subpatterns: List<FieldPattern>): PatternKind {
    return when (item) {
        is RsEnumVariant -> {
            PatternKind.Variant(TyAdt.valueOf(item.parentEnum), item.index, subpatterns)
        }
        is RsStructItem -> {
            PatternKind.Leaf(subpatterns)
        }
        else -> error("Impossible case $item")
    }
}


fun getFieldIndex(fieldsOwner: RsFieldsOwner, pat: RsPatField): Int {
    val identifier = pat.identifier
    return fieldsOwner.namedFields.map { it.identifier }.indexOfFirst {
        it.text == identifier?.text
    }
}

val RsEnumVariant.index: Int
    get() = parentEnum.enumBody?.enumVariantList?.indexOf(this) ?: -1


val RsPath.isEnumVariant: Boolean
    get() = (this.reference.resolve() as? RsEnumVariant) != null
val RsPath.isStructure: Boolean
    get() = (this.reference.resolve() as? RsStructItem) != null

val Constructor.size: Int
    get() {
        return when (this) {
            is Constructor.Single -> {
                when (this.pat) {
                    is RsPatStruct -> (this.pat.path.reference.resolve() as RsFieldsOwner).size
                    is RsPatTupleStruct -> (this.pat.path.reference.resolve() as RsFieldsOwner).size
                    else -> TODO("Check for another case")
                }
            }
            is Constructor.Variant -> {
                this.variant.size
            }
            is Constructor.ConstantValue -> 1
            is Constructor.ConstantRange -> TODO()
            is Constructor.Slice -> TODO()
        }
    }

val RsFieldsOwner.size: Int
    get() = tupleFields?.tupleFieldDeclList?.size ?: blockFields?.fieldDeclList?.size ?: 0

val RsPat.constructors: List<Constructor>
    get() {
        return when (this) {
            is RsPatStruct -> path.singleOrVariant(this)
            is RsPatTupleStruct -> path.singleOrVariant(this)
            is RsPatRef -> listOf(Constructor.Single(this))
            is RsPatUniq -> listOf(Constructor.Single(this)) // Не понимаю что это за шаблоны такие. Кажется `box a`
            is RsPatConst -> listOf(Constructor.ConstantValue(this, this.expr)) // Надо бы достать значение. Ну только если нужно
            is RsPatRange -> listOf(Constructor.ConstantRange(this, patConstList[0], patConstList[1], dotdotdot == null)) // TODO ..=
            is RsPatTup -> listOf(Constructor.Single(this)) // Вместе со структурой и енумом?
            is RsPatIdent, is RsPatWild -> emptyList()
            is RsPatMacro -> TODO()
            is RsPatSlice -> TODO()
            else -> emptyList()
        }
    }

fun RsPath.singleOrVariant(pat: RsPat): List<Constructor> {
    val item = reference.resolve()
    return if (item is RsEnumVariant) listOf(Constructor.Variant(pat, item))
    else listOf(Constructor.Single(pat))
}


val List<List<*>>.width: Int
    get() = maxWith(Comparator.comparing<List<*>, Int> { it.size })?.size ?: 0

val List<List<*>>.height: Int
    get() = size
