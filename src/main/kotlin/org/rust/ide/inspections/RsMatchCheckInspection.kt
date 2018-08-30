package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            val type = (o.expr?.type ?: return) as? TyPrimitive ?: return
            val matrix = o.matchBody?.matchArmList?.map { arm ->
                arm.patList.map { lowerPattern(it) } to arm.matchArmGuard
            } ?: emptyList()
            matrix.forEach {
                println(it)
            }
            /*for ((i, row) in matrix.withIndex()) {
                if(i > 0) {
                    val tmp = matrix.subList(0, i).map {
                        specializeRow(row.first.first().constructors?.first(), it.first)
                    }
                    tmp.forEach {
                        if(it != null) {
                            val ref = o.matchBody?.matchArmList?.get(i) ?: error("Can't get reference")
                            holder.registerProblem(ref, "heh", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        }
                    }
                }
            }*/
        }

    }
}

// ************************************************** ALGORITHM **************************************************

// TODO возможно сюда нужно передавать дополнительную информацию для опеределения ноды в дереве
fun checkArms(arms: List<Pair<List<Pattern>, RsMatchArmGuard?>>, holder: ProblemsHolder) {
    println("<top>.checkArms(arms = $arms, holder = $holder)")
    val seen = mutableListOf<List<Pattern>>()
    var catchAll = false

    for ((index, pair) in arms.withIndex()) {
        for (pattern in pair.first) {
            val v = listOf(pattern)

            when (isUseful(seen, v)) {
                false -> false //TODO()
                true -> true //TODO()
            }
            if (pair.second == null) {
                seen.add(v)
                if (catchAll == false && false/*check catchall for RsPat*/) catchAll = true
            }
        }
    }

}

// Use algorithm from 3.1 http://moscova.inria.fr/~maranget/papers/warn/warn004.html
fun isUseful(matrix: List<List<Pattern>?>, v: List<Pattern>): Boolean {
    println("<top>.isUseful(matrix = $matrix, v = $v)")

    //// Base

    // Base case if we are pattern-matching on ()
    if (matrix.width == 0) {
        return matrix.height == 0
    }

    //// Induction


    val constructors = v[0].constructors

    println("<top>.isUseful expand first col: expanding ${v[0]}")
    return if (constructors != null) {
        println("<top>.isUseful expanding constructors $constructors")
        constructors.map {
            isUsefulS(matrix, v, it)
        }.find { it == true } ?: false
    } else {
        println("<top>.isUseful expanding wildcard")
        val usedConstrucors = matrix.mapNotNull { it?.get(0) }.mapNotNull { it.constructors?.first() }
        println("<top>.isUseful used constructors $usedConstrucors")
        TODO()

        false
    }
}

fun isUsefulS(matrix: List<List<Pattern>?>, v: List<Pattern>, constructor: Constructor): Boolean {
    val subTys = constructor.ty.subTys()
    val wildPattern = subTys.map { Pattern(it, PatternKind.Wild) }

    val newMatrix = matrix.map { specializeRow(it, constructor) }

    val newV = specializeRow(v, constructor)
    return when (newV) {
        null -> false
        else -> isUseful(newMatrix, newV)
    }
}

fun specializeRow(row: List<Pattern>?, constructor: Constructor): List<Pattern>? {
    println("<top>.specializeRow(constructor = $constructor, row = $row)")
    row ?: return null // FIXME не уверен в надобности

    val pat = row[0]
    val kind = pat.kind
    val head: List<Pattern>? = when (kind) {
        is PatternKind.Variant -> {
            if (constructor == pat.constructors?.first()) {
                patternsForVariant(kind.subpatterns, constructor.size)
            } else {
                emptyList()
            }
        }
        is PatternKind.Leaf -> {
            /**
             * Вот здесь непонятно. В соотвествии с алгоритом необходимо проверить равенство конструкторов
             * (как в случае с вариантом). Но в компиляторе они так не делают. Очень странно. И непонятно.
             */
            patternsForVariant(kind.subpatterns, constructor.size)
        }
        is PatternKind.Deref -> listOf(kind.subpattern)
        is PatternKind.Constant -> {
            when (constructor) {
                is Constructor.Slice -> TODO()
                else -> {
                    when (constructor) {
                        is Constructor.Single -> TODO()
                        is Constructor.Variant -> TODO()
                        is Constructor.ConstantValue -> {
                            if (compareConstValue(constructor.expr, kind.value) == 0) {
                                emptyList<Pattern>()
                            } else {
                                null
                            }
                        }
                        is Constructor.ConstantRange -> TODO()
                        is Constructor.Slice -> TODO()
                    }
                }
            }
        }
        is PatternKind.Range -> TODO()
        is PatternKind.Slice -> TODO()
        is PatternKind.Array -> TODO()
        PatternKind.Wild, is PatternKind.Binding -> {
            val result = mutableListOf<Pattern>()
            repeat(constructor.size) {
                result.add(Pattern(TyUnknown, PatternKind.Wild))
            }
            result
        }
    }

    return head?.plus(row.subList(1, row.size))
}

// ************************************************** CUSTOM DATA **************************************************
sealed class Constructor(open val ty: Ty) {

    /// The constructor of all patterns that don't vary by constructor,
    /// e.g. struct patterns and fixed-length arrays.
    //Single
    data class Single(override val ty: Ty) : Constructor(ty)

    /// Enum variants.
    // Variant(DefId)
    data class Variant(val variant: RsEnumVariant, override val ty: TyAdt) : Constructor(ty)

    /// Literal values.
    //ConstantValue(&'tcx ty::Const<'tcx>),
    data class ConstantValue(val expr: RsExpr, override val ty: Ty) : Constructor(ty)

    /// Ranges of literal values (`2...5` and `2..5`).
    //ConstantRange(&'tcx ty::Const<'tcx>, &'tcx ty::Const<'tcx>, RangeEnd),
    data class ConstantRange(val start: RsPatConst, val end: RsPatConst, val includeEnd: Boolean = false, override val ty: Ty) : Constructor(ty)

    /// Array patterns of length n.
    //Slice(u64),
    data class Slice(val size: Int, override val ty: Ty) : Constructor(ty)
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

// ************************************************** UTILS FUNC **************************************************
fun lowerPattern(pat: RsPat): Pattern {
    println("<top>.lowerPattern(pat = $pat)")
    val kind = pat.kind
    val ty = pat.type
    return Pattern(ty, kind)
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

fun patternsForVariant(subpatterns: List<FieldPattern>, size: Int): List<Pattern> {
    val result = mutableListOf<Pattern>()
    repeat(size) {
        result.add(Pattern(TyUnknown, PatternKind.Wild))
    }
    for (subpattern in subpatterns) {
        result[subpattern.first] = subpattern.second
    }
    return result
}

fun compareConstValue(a: RsExpr, b: RsExpr): Int? {
    return when {
        a is RsLitExpr && b is RsLitExpr -> compareLiteral(a, b)
        a is RsPathExpr && b is RsPathExpr -> {
            val aR = a.path.reference.resolve()
            val bR = b.path.reference.resolve()
            if (aR?.equals(bR) == true) 0
            else null
        }
        a is RsUnaryExpr && b is RsUnaryExpr -> TODO()
        else -> null
    }

}

fun compareLiteral(a: RsLitExpr, b: RsLitExpr): Int? {
    val aKind = a.kind ?: return null
    val bKind = b.kind ?: return null
    return when {
        aKind is RsLiteralKind.Boolean && bKind is RsLiteralKind.Boolean -> {
            when {
                aKind.value == bKind.value -> 0
                aKind.value && !bKind.value -> 1
                !aKind.value && bKind.value -> -1
                else -> null
            }
        }
        aKind is RsLiteralKind.Integer && bKind is RsLiteralKind.Integer -> {
            val aV = aKind.value
            val bV = bKind.value
            if (aV == null || bV == null) null
            else aV.compareTo(bV)
        }
        aKind is RsLiteralKind.Float && bKind is RsLiteralKind.Float -> {
            val aV = aKind.value
            val bV = bKind.value
            if (aV == null || bV == null) null
            else aV.compareTo(bV)
        }
        aKind is RsLiteralKind.String && bKind is RsLiteralKind.String -> {
            val aV = aKind.value
            val bV = bKind.value
            if (aV == null || bV == null) null
            else aV.compareTo(bV)
        }
        aKind is RsLiteralKind.Char && bKind is RsLiteralKind.Char -> {
            val aV = aKind.value
            val bV = bKind.value
            if (aV == null || bV == null) null
            else aV.compareTo(bV)
        }
        else -> null
    }
}

fun RsFieldsOwner.indexOf(pat: RsPatField): Int {
    val identifier = pat.identifier
    return namedFields.map { it.identifier }.indexOfFirst {
        it.text == identifier?.text
    }
}

fun Ty.subTys(): List<Ty> {
    return when (this) {
        is TyTuple -> {
            this.types
        }
        is TyAdt -> {
            this.typeArguments
        }
        else -> {
            emptyList()
        }
    }
}

// ************************************************** UTILS VAL **************************************************
val Constructor.size: Int
    get() {
        return when (this) {
            is Constructor.Single -> {
                ty.subTys().size
            }
            is Constructor.Variant -> {
                this.variant.size
            }
            is Constructor.ConstantValue -> ty.subTys().size
            is Constructor.ConstantRange -> TODO()
            is Constructor.Slice -> TODO()
        }
    }

val Pattern.constructors: List<Constructor>?
    get() {
        return when (kind) {
            PatternKind.Wild, is PatternKind.Binding -> null
            is PatternKind.Variant -> {
                val enum = (ty as TyAdt).item as RsEnumItem
                val variant = enum.enumBody?.enumVariantList?.get(kind.variantIndex) ?: return emptyList()
                listOf(Constructor.Variant(variant, ty))
            }
            is PatternKind.Leaf, is PatternKind.Deref -> listOf(Constructor.Single(ty))
            is PatternKind.Constant -> listOf(Constructor.ConstantValue(kind.value, ty))
            is PatternKind.Range -> TODO()
            is PatternKind.Slice -> TODO()
            is PatternKind.Array -> TODO()
        }
    }

val RsPat.type: Ty
    get() {
        return when (this) {
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
                when (tmp) {
                    is RsEnumVariant -> {
                        TyAdt.valueOf(tmp.parentEnum)
                    }
                    is RsStructOrEnumItemElement -> {
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
                    (item as RsFieldsOwner).indexOf(patField) to pattern
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

val RsEnumVariant.index: Int
    get() = parentEnum.enumBody?.enumVariantList?.indexOf(this) ?: -1

val RsFieldsOwner.size: Int
    get() = tupleFields?.tupleFieldDeclList?.size ?: blockFields?.fieldDeclList?.size ?: 0

val List<List<*>?>.width: Int
    get() = maxWith(Comparator.comparing<List<*>, Int> { it.size })?.size ?: 0

val List<List<*>?>.height: Int
    get() = size
