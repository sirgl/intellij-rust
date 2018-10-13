package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.fixes.AddWildcardArmFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsMatchCheckInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Match Check" //FIXME name

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(o: RsMatchExpr) {

            println("**************************************** NEW ****************************************")

            checkExhaustive(o, holder)
//
//            val matrix = o.matrix
//            matrix.forEach {
//                println(it)
//            }
//            println()
//            for ((i, row) in matrix.withIndex()) {
//                if (i > 0) {
//                    val useful = isUseful(matrix.subList(0, i).map { it.first }, row.first, false)
//                    println("match arm ${row.first} isUseful=$useful")
//                    println()
//                    if (!useful.isUseful()) {
//                        val matchArm = o.matchBody?.matchArmList?.get(i) ?: continue
//                        holder.registerProblem(
//                            matchArm,
//                            "Useless match arm",
//                            ProblemHighlightType.GENERIC_ERROR,
//                            SubstituteTextFix.delete("Remove useless arm", o.containingFile, matchArm.textRange)
//                        )
//                    }
//                }
//            }
        }

    }
}

// ************************************************** ALGORITHM **************************************************

fun checkExhaustive(match: RsMatchExpr, holder: ProblemsHolder) {
    println("<top>.checkExhaustive(match = $match, holder = $holder)")
    val matrix = match.matrix
    val useful = isUseful(matrix.map { it.first }, listOf(Pattern(TyUnknown, PatternKind.Wild)), true)
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
        Usefulness.NotUseful -> println("<top>.checkExhaustive useful=$useful")
    }

    if (useful.isUseful()) {

    }
}

/*fun checkArms(arms: List<Pair<List<Pattern>, RsMatchArmGuard?>>, holder: ProblemsHolder) {
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
                if (catchAll == false && false*//*check catchall for RsPat*//*) catchAll = true
            }
        }
    }
}*/

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
            Usefulness.NotUseful
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
        }.find { it.isUseful() } ?: Usefulness.NotUseful
    } else {
        println("<top>.isUseful expanding wildcard")
        val usedConstructors = matrix.map { it?.get(0)?.constructors }.flatMap { it ?: emptyList() }
        println("<top>.isUseful used constructors $usedConstructors")


        val allConstructors = allConstructors(type)
        println("<top>.isUseful allConstructors $allConstructors")

        val missingConstructor = allConstructors.filter { !usedConstructors.contains(it) }


        val isPrivatelyEmpty = allConstructors.isEmpty()
        val isDeclaredNonexhaustive = (type as? TyAdt)?.isNonExhaustiveEnum ?: false
        println("<top>.isUseful missing constructors $missingConstructor\tis privately empty $isPrivatelyEmpty, " +
            "is declared nonexhaustive $isDeclaredNonexhaustive")

        val isNonExhaustive = isPrivatelyEmpty || isDeclaredNonexhaustive

        return if (missingConstructor.isEmpty() && !isNonExhaustive) {
            allConstructors.map {
                isUsefulS(matrix, v, it, type, withWitness)
            }.find {
                it.isUseful()
            } ?: Usefulness.NotUseful
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

    val newMatrix = matrix.mapNotNull { specializeRow(it, constructor) }
    println("<top>.isUsefulS newMatrix=$newMatrix")

    val newV = specializeRow(v, constructor)
    return when (newV) {
        null -> Usefulness.NotUseful
        else -> {
            val useful = isUseful(newMatrix, newV, withWitness)
            when (useful) {
                is Usefulness.UsefulWithWitness -> {
                    Usefulness.UsefulWithWitness(useful.witness.map {
                        it.applyConstructors(constructor, type)
                        it
                    })
                }
                else -> useful
            }
        }
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
                patternsForVariant(kind.subpatterns, constructor.arity)
            } else {
                null
            }
        }
        is PatternKind.Leaf -> {
            /**
             * Вот здесь непонятно. В соотвествии с алгоритом необходимо проверить равенство конструкторов
             * (как в случае с вариантом). Но в компиляторе они так не делают. Очень странно. И непонятно.
             */
            patternsForVariant(kind.subpatterns, constructor.arity)
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
            repeat(constructor.arity) {
                result.add(Pattern(TyUnknown, PatternKind.Wild))
            }
            result
        }
    }
    println("<top>.specializeRow head=$head")
    return head?.plus(row.subList(1, row.size))
}

// ************************************************** CUSTOM DATA **************************************************

class Witness(val patterns: MutableList<Pattern>) {
    override fun toString() = patterns.toString()

    fun pushWildCtor(constructor: Constructor, type: Ty) {
        println("Witness.pushWildCtor(constructor = $constructor, type = $type)")
        val subPatterns = constructor.subTys(type)
        println("Witness.pushWildCtor subPatterns=$subPatterns")
        subPatterns.forEach {
            patterns.add(Pattern(it, PatternKind.Wild))
        }
        applyConstructors(constructor, type)
    }

    fun applyConstructors(constructor: Constructor, type: Ty) {
        println("Witness.applyConstructors(constructor = $constructor, type = $type)")
        val arity = constructor.arity
        val len = patterns.size
        val pats = patterns.subList(len - arity, len).reversed()
        val pat = when (type) {
            is TyAdt, is TyTuple -> {
//                val pats = patterns.subList((len - arity).takeIf { it >= 0 } ?: 0, len).mapIndexed { index, pattern ->
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
        patterns.add(Pattern(type, pat))
        println("Witness.applyConstructors patterns=$patterns")
    }
}

sealed class Usefulness {
    class UsefulWithWitness(val witness: List<Witness>) : Usefulness()
    object Useful : Usefulness()
    object NotUseful : Usefulness()
}

sealed class Constructor(open val ty: Ty) {

    /// The constructor of all patterns that don't vary by constructor,
    /// e.g. struct patterns and fixed-length arrays.
    //Single
    data class Single(override val ty: Ty) : Constructor(ty)

    /// Enum variants.
    data class Variant(val variant: RsEnumVariant, val index: Int, override val ty: TyAdt) : Constructor(ty)

    /// Literal values.
    data class ConstantValue(val value: Constant, override val ty: Ty) : Constructor(ty)

    /// Ranges of literal values (`2...5` and `2..5`).
    data class ConstantRange(val start: Constant, val end: Constant, val includeEnd: Boolean = false, override val ty: Ty) : Constructor(ty)

    /// Array patterns of length n.
    data class Slice(val size: Int, override val ty: Ty) : Constructor(ty)
}

typealias FieldPattern = Pair<Int, Pattern>

data class Pattern(val ty: Ty, val kind: PatternKind) {
    override fun toString(): String {
        return when (kind) {
            PatternKind.Wild -> "_"
            is PatternKind.Binding -> "x"
            is PatternKind.Variant -> {
                val enum = kind.ty.item as RsEnumItem
                val variant = enum.enumBody?.enumVariantList?.get(kind.variantIndex) ?: return ""
                "${enum.identifier?.text ?: ""}::${variant.identifier.text}" + if(!kind.subpatterns.isEmpty()) {
                    "(${kind.subpatterns.sortedBy { it.first }.joinToString { it.second.toString() }})"
                } else ""
            }
            is PatternKind.Leaf -> {
                val subpatterns = kind.subpatterns.sortedBy { it.first }
                when (ty) {
                    is TyTuple -> {
                        subpatterns.joinToString(", ", "(", ")") { pattern ->
                            pattern.second.toString()
                        }
                    }
                    is TyAdt -> {
                        val struct = ty.item as RsStructItem
                        (struct.identifier?.text ?: "") + when {
                            struct.blockFields != null -> {
                                subpatterns.joinToString(",", "{", "}") { pattern ->
                                    "${struct.blockFields?.fieldDeclList?.get(pattern.first)?.identifier?.text}: ${pattern.second}"
                                }
                            }
                            struct.tupleFields != null -> {
                                subpatterns.joinToString(",", "(", ")") { pattern ->
                                    "${pattern.second}"
                                }
                            }
                            else -> TODO("struct has no fields")
                        }
                    }
                    else -> ""
                }
            }
            is PatternKind.Deref -> kind.toString()
            is PatternKind.Constant -> kind.value.toString()
            is PatternKind.Range -> TODO()
            is PatternKind.Slice -> TODO()
            is PatternKind.Array -> TODO()
        }
    }
}

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
    data class Variant(val ty: TyAdt, val variantIndex: Int, val subpatterns: List<FieldPattern>) : PatternKind()

    /// (...), Foo(...), Foo{...}, or Foo, where `Foo` is a variant name from an adt with 1 variant
    data class Leaf(val subpatterns: List<FieldPattern>) : PatternKind()

    /// box P, &P, &mut P, etc
    data class Deref(val subpattern: Pattern) : PatternKind()

    data class Constant(val value: org.rust.ide.inspections.Constant) : PatternKind()

    data class Range(val lc: org.rust.ide.inspections.Constant, val rc: org.rust.ide.inspections.Constant, val included: Boolean) : PatternKind()

    /// matches against a slice, checking the length and extracting elements.
    /// irrefutable when there is a slice pattern and both `prefix` and `suffix` are empty.
    /// e.g. `&[ref xs..]`.
    data class Slice(val prefix: List<Pattern>, val slice: Pattern?, val suffix: List<Pattern>) : PatternKind()

    /// fixed match against an array, irrefutable
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


fun Usefulness.isUseful(): Boolean = when (this) {
    Usefulness.NotUseful -> false
    else -> true
}

fun allConstructors(ty: Ty): List<Constructor> {
    println("<top>.allConstructors(ty = $ty)")
    // TODO check uninhabited
    return when {
        ty === TyBool -> listOf(true, false).map {
            Constructor.ConstantValue(Constant.Boolean(it), ty)
        }
        ty is TyAdt && ty.item is RsEnumItem -> ty.item.enumBody?.enumVariantList?.map {
            Constructor.Variant(it, it.index, ty)
        } ?: emptyList()

        ty is TyArray && ty.size != null -> TODO()
        ty is TyArray || ty is TySlice -> TODO()

        else -> listOf(Constructor.Single(ty))
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
        is RsEnumVariant -> PatternKind.Variant(TyAdt.valueOf(item.parentEnum), item.index, subpatterns)
        is RsStructItem -> PatternKind.Leaf(subpatterns)
        else -> error("Impossible case $item")
    }
}

fun patternsForVariant(subpatterns: List<FieldPattern>, size: Int): List<Pattern> {
    println("<top>.patternsForVariant(subpatterns = $subpatterns, size = $size)")
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
    return when {
        a is Constant.Boolean && b is Constant.Boolean -> when {
            a.value == b.value -> 0
            a.value && !b.value -> 1
            else -> -1
        }
        a is Constant.Integer && b is Constant.Integer -> a.value.compareTo(b.value)

        a is Constant.Double && b is Constant.Double -> a.value.compareTo(b.value)

        a is Constant.String && b is Constant.String -> a.value.compareTo(b.value)

        a is Constant.Char && b is Constant.Char -> a.value.compareTo(b.value)

        a is Constant.Path && b is Constant.Path -> {
            val aR = a.value.path.reference.resolve()
            val bR = b.value.path.reference.resolve()
            if (aR?.equals(bR) == true) 0
            else null
        }
        else -> 0
    }
}

fun RsFieldsOwner.indexOf(pat: RsPatField): Int {
    val identifier = pat.identifier
    return namedFields.map { it.identifier }.indexOfFirst {
        it.text == identifier?.text
    }
}

fun Ty.subTys(): List<Ty> {
    println("<top>.subTys() ty=$this")
    return when (this) {
        is TyTuple -> {
            println("<top>.subTys this is tuple subty=${this.types}")
            this.types
        }
        is TyAdt -> {
            println("<top>.subTys this is adt subty=$typeArguments")
            this.typeArguments
        }
        else -> {
            println("<top>.subTys this is something subty=${emptyList<Ty>()}")
            emptyList()
        }
    }
}

fun Constructor.subTys(type: Ty): List<Ty> {
    println("<top>.subTys(type = $type)")
    return when (type) {
        is TyTuple -> {
            type.types
        }
        is TySlice, is TyArray -> when (this) {
            is Constructor.Slice -> {
                (0..(this.size - 1)).map { ty }
            }
            is Constructor.ConstantValue -> listOf()
            else -> error("bad slice pattern")
        }
        is TyReference -> listOf(type.referenced)
        is TyAdt -> {
            // TODO check box
            // TODO ok?
            when (this) {
                is Constructor.Single -> TODO()
                is Constructor.Variant -> {
                    this.variant.tupleFields?.tupleFieldDeclList?.map { it.typeReference.type }
                        ?: this.variant.blockFields?.fieldDeclList?.mapNotNull { it.typeReference?.type }
                        ?: run {
                            println("NOTHING IN SUB TYS")
                            listOf<Ty>()
                        }
                }
                else -> {
                    println("AAA NOTHING IN SUB TYS")
                    listOf()
                }
            }
        }
        else -> listOf()
    }
}

// ************************************************** UTILS VAL **************************************************
val RsMatchExpr.matrix: List<Pair<List<Pattern>, RsMatchArmGuard?>>
    get() = matchBody?.matchArmList?.map { arm ->
        arm.patList.map { lowerPattern(it) } to arm.matchArmGuard
    } ?: emptyList()


val TyAdt.isNonExhaustiveEnum: Boolean
    get() {
        val enum = item as? RsEnumItem ?: return false
        val attrList = enum.outerAttrList
        return attrList.any { it.metaItem.name == "non_exhaustive" }
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

/*fun Constructor.getArity(type: Ty): Int {
    println("<top>.getArity(type = $type)")
    return when (type) {
        is TyTuple -> {
            type.types.size
        }
        is TySlice, is TyArray -> when (this) {
            is Constructor.Slice -> this.size
            is Constructor.ConstantValue -> 0
            else -> error("bad slice pattern")
        }
        is TyReference -> 1
        is TyAdt -> {
            val item = type.item
            when(item) {
                is RsEnumItem -> {
                    val variant = this as Constructor.Variant
//                    val variant = item.enumBody?.enumVariantList?.get(v.index)
                    variant.variant.tupleFields?.tupleFieldDeclList?.size ?: variant.variant.blockFields?.fieldDeclList?.size ?: 0

                }
                is RsStructItem -> {
                    val s = type.item as? RsStructItem ?: return 0
                    s.tupleFields?.tupleFieldDeclList?.size ?: s.blockFields?.fieldDeclList?.size ?: 0
                }
                else -> 0
            }
        }
        else -> 0
    }


}*/

val Constructor.arity: Int
    get() {
        return when (this) {
            is Constructor.Single -> ty.size
            is Constructor.ConstantValue -> ty.subTys().size
            is Constructor.Variant -> variant.size

            is Constructor.ConstantRange -> TODO()
            is Constructor.Slice -> TODO()
        }
    }

val Ty.size: Int
    get() {
        println("<top>.Ty.size() ty=$this")
        return when (this) {
            is TyTuple -> {
                println("<top>.Ty.size() this is type ${types.size} ")
                this.types.size
            }
            is TyAdt -> {
                println("<top>.Ty.size() this is adt adt=$this")
                val structOrEnum = item
                when (structOrEnum) {
                    is RsStructItem -> structOrEnum.size
                    is RsEnumItem -> typeArguments.size
                    else -> 0
                }
            }
            else -> 0

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
            is RsPatConst -> expr.type
            is RsPatStruct, is RsPatTupleStruct -> {
                val path = when (this) {
                    is RsPatTupleStruct -> path
                    is RsPatStruct -> path
                    else -> null
                }
                val tmp = path?.reference?.resolve()
                when (tmp) {
                    is RsEnumVariant -> TyAdt.valueOf(tmp.parentEnum)
                    is RsStructOrEnumItemElement -> TyAdt.valueOf(tmp)
                    else -> TyUnknown
                }
            }
            is RsPatWild -> TyUnknown
            is RsPatIdent -> patBinding.type
            is RsPatTup -> TyTuple(patList.map { it.type })
            is RsPatRange -> {
                TODO()
            }

            is RsPatRef -> TODO()
            is RsPatUniq -> TODO()
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
                        PatternKind.Variant(ty, variant.index, emptyList())
                    } else {
                        error("Unresolved constant")
                    }
                } else {
                    PatternKind.Constant(expr.value)
                }
            }
            is RsPatRange -> {
                println("<top>.RsPat.kind() RsPatRange")
                val a = this.patConstList.first()
                val b = this.patConstList.last()
                PatternKind.Range(a.expr.value, b.expr.value, this.dotdoteq != null)
            }
            is RsPatRef -> TODO()
            is RsPatUniq -> TODO()
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
