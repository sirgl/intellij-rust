package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {
            println("**************************************** NEW ****************************************")
            val matrix = o.matchBody?.matchArmList?.map { arm ->
                arm.patList.map { lowerPattern(it) } to arm.matchArmGuard
            } ?: emptyList()
            matrix.forEach {
                println(it)
            }
            println()
            for ((i, row) in matrix.withIndex()) {
                if (i > 0) {
                    val useful = isUseful(matrix.subList(0, i).map { it.first }, row.first)
                    println("match arm ${row.first} isUseful=$useful")
                    println()
                    if (!useful) {
                        val matchArm = o.matchBody?.matchArmList?.get(i) ?: continue
                        holder.registerProblem(matchArm, "Useless match arm", ProblemHighlightType.GENERIC_ERROR)
                    }
                }
            }
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
        val usedConstructors = matrix.map { it?.get(0)?.constructors }.flatMap { it ?: emptyList() }
        println("<top>.isUseful used constructors $usedConstructors")

        val ty = matrix.mapNotNull { it?.get(0) }
            .map { it.ty }
            .find { it != TyUnknown } ?: v[0].ty

        val allConstructors = allConstructors(ty)
        println("<top>.isUseful allConstructors $allConstructors")

        val missingConstructor = allConstructors.filter { !usedConstructors.contains(it) }


        val isPrivatelyEmpty = allConstructors.isEmpty()
        val isDeclaredNonexhaustive = (ty as? TyAdt)?.isNonExhaustiveEnum ?: false
        println("<top>.isUseful missing constructors $missingConstructor\tis privately empty $isPrivatelyEmpty, is declared nonexhaustive $isDeclaredNonexhaustive")

        val isNonExhaustive = isPrivatelyEmpty || isDeclaredNonexhaustive

        return if (missingConstructor.isEmpty() && !isNonExhaustive) {
            allConstructors.map {
                isUsefulS(matrix, v, it)
            }.find {
                it
            } ?: false
        } else {
            val newMatrix = matrix.map {
                if (it?.get(0)?.kind is PatternKind.Wild) {
                    it.subList(1, it.size)
                } else {
                    null
                }
            }.mapNotNull { it }
            isUseful(newMatrix, v.subList(1, v.size))
        }
    }
}

fun isUsefulS(matrix: List<List<Pattern>?>, v: List<Pattern>, constructor: Constructor): Boolean {
//    val subTys = constructor.ty.subTys()
//    val wildPattern = subTys.map { Pattern(it, PatternKind.Wild) }
    println("<top>.isUsefulS(matrix = $matrix, v = $v, constructor = $constructor)")

    val newMatrix = matrix.map { specializeRow(it, constructor) }.mapNotNull { it }
    println("<top>.isUsefulS newMatrix=$newMatrix")

    val newV = specializeRow(v, constructor)
    return when (newV) {
        null -> false
        else -> isUseful(newMatrix, newV)
    }
}

fun specializeRow(row: List<Pattern>?, constructor: Constructor): List<Pattern>? {
    println("<top>.specializeRow(row = $row, constructor = $constructor)")
    row ?: return null // FIXME не уверен в надобности

    val pat = row[0]
    val kind = pat.kind
    val head: List<Pattern>? = when (kind) {
        is PatternKind.Variant -> {
            if (constructor == pat.constructors?.first()) {
                patternsForVariant(kind.subpatterns, constructor.size)
            } else {
                null
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
                            if (compareConstValue(constructor.value, kind.value) == 0) {
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
    println("<top>.specializeRow head=$head")
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
    data class Variant(val variant: RsEnumVariant, val index: Int, override val ty: TyAdt) : Constructor(ty)

    /// Literal values.
    //ConstantValue(&'tcx ty::Const<'tcx>),
    data class ConstantValue(val value: Constant, override val ty: Ty) : Constructor(ty)

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
    data class Constant(val value: org.rust.ide.inspections.Constant) : PatternKind()

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

sealed class Constant {
    data class Boolean(val value: kotlin.Boolean) : Constant()
    data class Integer(val value: Int) : Constant()
    data class Double(val value: kotlin.Double) : Constant()
    data class String(val value: kotlin.String) : Constant()
    data class Char(val value: kotlin.String) : Constant()
    data class Path(val value: RsPathExpr) : Constant()
    object Unknown : Constant()
}

// ************************************************** UTILS FUNC **************************************************
fun allConstructors(ty: Ty): List<Constructor> {
    println("<top>.allConstructors(ty = $ty)")
    // TODO check uninhabited
    return when {
        ty is TyBool -> {
            listOf(true, false).map {
                Constructor.ConstantValue(Constant.Boolean(it), ty)
            }
        }
        ty is TyArray && ty.size != null -> TODO()
        ty is TyArray || ty is TySlice -> TODO()

        ty is TyAdt && ty.item is RsEnumItem -> {
            ty.item.enumBody?.enumVariantList?.map { Constructor.Variant(it, it.index, ty) } ?: emptyList()
        }
        else -> {
            listOf(Constructor.Single(ty))
        }

    }
}

fun lowerPattern(pat: RsPat): Pattern {
    println("<top>.lowerPattern(pat = $pat)")
    val kind = pat.kind
    val ty = pat.type
    println("<top>.lowerPattern ty=$ty, kind=$kind")
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

fun compareConstValue(a: Constant, b: Constant): Int? {
    when {
        a is Constant.Boolean && b is Constant.Boolean -> {
            return when {
                a.value == b.value -> 0
                a.value && !b.value -> 1
                else -> -1
            }
        }
        a is Constant.Integer && b is Constant.Integer -> {
            return a.value.compareTo(b.value)
        }
        a is Constant.Double && b is Constant.Double -> {
            return a.value.compareTo(b.value)
        }
        a is Constant.String && b is Constant.String -> {
            return a.value.compareTo(b.value)
        }
        a is Constant.Char && b is Constant.Char -> {
            return a.value.compareTo(b.value)
        }
        a is Constant.Path && b is Constant.Path -> {
            val aR = a.value.path.reference.resolve()
            val bR = b.value.path.reference.resolve()
            return if (aR?.equals(bR) == true) 0
            else null
        }
        else -> return 0
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

/*fun isUninhabited(ty: Ty): Boolean {
    if(EXHAUSTIVE_PATTERNS.state == FeatureState.ACTIVE) {
        tre
    } else {
        false
    }
}*/

// ************************************************** UTILS VAL **************************************************
val TyAdt.isNonExhaustiveEnum: Boolean
    get() {
        val enum = item as? RsEnumItem ?: return false
        val attrList = enum.outerAttrList
        val attr = attrList.find {
            it.metaItem.name == "non_exhaustive"
        }
        return attr != null
    }

val RsExpr.value: Constant
    get() {
        return when (this) {
            is RsLitExpr -> {
                val kind = kind
                when (kind) {
                    is RsLiteralKind.Boolean -> Constant.Boolean(kind.value)
                    is RsLiteralKind.Integer -> Constant.Integer(kind.value ?: 0)
                    is RsLiteralKind.Float -> Constant.Double(kind.value ?: 0.0)
                    is RsLiteralKind.String -> Constant.String(kind.value ?: "")
                    is RsLiteralKind.Char -> Constant.Char(kind.value ?: "")
                    null -> Constant.Unknown
                }
            }
            is RsPathExpr -> {
                Constant.Path(this)
            }
            is RsUnaryExpr -> TODO()
            else -> TODO()
        }
    }

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
                listOf(Constructor.Variant(variant, variant.index, ty))
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
        println("<top>.RsPat.kind()")
        return when (this) {
            is RsPatIdent -> {
                println("<top>.RsPat.kind() RsPatIdent")
                PatternKind.Binding(this.patBinding.type)
            }
            is RsPatWild -> {
                println("<top>.RsPat.kind() RsPatWild")
                PatternKind.Wild
            }
            is RsPatTup -> {
                println("<top>.RsPat.kind() RsPatTup")
                PatternKind.Leaf(this.patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat)
                })
            }
            is RsPatStruct -> {
                println("<top>.RsPat.kind() RsPatStruct")
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
                println("<top>.RsPat.kind() RsPatTupleStruct")
                val item = path.reference.resolve() ?: error("Can't resolve ${path.text}")
                val subpatterns: List<FieldPattern> = patList.mapIndexed { i, pat ->
                    i to lowerPattern(pat) // TODO patIdent ok?
                }

                getLeafOrVariant(item, subpatterns)
            }
            is RsPatConst -> {
                println("<top>.RsPat.kind() RsPatConst")
                val ty = this.expr.type
                if (ty is TyAdt) {
                    println("<top>.RsPat.kind() RsPatConst.expr is TyAdt ")
                    if (ty.item is RsEnumItem) {
                        println("<top>.RsPat.kind() RsPatConst.expr it EnumVariant")
                        val variant = (expr as RsPathExpr).path.reference.resolve() as RsEnumVariant
                        PatternKind.Variant(ty, (variant as RsEnumVariant).index, emptyList())
                    } else {
                        error("Unresolved constant")
                    }
                } else {
                    PatternKind.Constant(expr.value)
                }
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
    get() = maxWith(Comparator.comparing<List<*>, Int> { it?.size ?: -1 })?.size ?: 0

val List<List<*>?>.height: Int
    get() = size
