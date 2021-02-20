/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("LoopToCallChain")

package org.rust.lang.core.resolve

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.RsConstants
import org.rust.lang.core.macros.MACRO_CRATE_IDENTIFIER_PREFIX
import org.rust.lang.core.macros.findMacroCallExpandedFrom
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsFile.Attributes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.NameResolutionTestmarks.missingMacroUse
import org.rust.lang.core.resolve.NameResolutionTestmarks.otherVersionOfSameCrate
import org.rust.lang.core.resolve.NameResolutionTestmarks.selfInGroup
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.indexes.RsMacroIndex
import org.rust.lang.core.resolve.ref.DotExprResolveVariant
import org.rust.lang.core.resolve.ref.FieldResolveVariant
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.foldTyTypeParameterWith
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.toTypeSubst
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark
import org.rust.openapiext.hitOnFalse
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.toPsiFile
import org.rust.stdext.buildList

// IntelliJ Rust name resolution algorithm.
// Collapse all methods (`ctrl shift -`) to get a bird's eye view.
//
// The entry point is
// `process~X~ResolveVariants(x: RsReferenceElement, processor: RsResolveProcessor)`
// family of methods.
//
// Conceptually, each of these methods returns a sequence of `RsNameElement`s
// visible at the reference location. During completion, all of them are presented
// as completion variants, and during resolve only the one with the name matching
// the reference is selected.
//
// Instead of Kotlin `Sequence`'s, a callback (`RsResolveProcessor`) is used, because
// it gives **much** nicer stacktraces (we used to have `Sequence` here some time ago).
//
// Instead of using `RsNameElement` directly, `RsResolveProcessor` operates on `ScopeEntry`s.
// `ScopeEntry` allows to change the effective name of an element (for aliases) and to retrieve
// the actual element lazily.
//
// The `process~PsiElement~Declarations` family of methods list name elements belonging
// to a particular element (for example, variants of an enum).
//
// Technicalities:
//
//   * We can get into infinite loop during name resolution. This is handled by
//     `RsReferenceCached`.
//   * The results of name resolution are cached and invalidated on every code change.
//     Caching also is handled by `RsReferenceCached`.
//   * Ideally, all of the methods except for `processLexicalDeclarations` should operate on stubs only.
//   * Rust uses two namespaces for declarations ("types" and "values"). The necessary namespace is
//     determined by the syntactic position of the reference in `processResolveVariants` function and
//     is passed down to the `processDeclarations` functions.
//   * Instead of `getParent` we use `getContext` here. This trick allows for funny things like creating
//     a code fragment in a temporary file and attaching it to some existing file. See the usages of
//     [RsCodeFragmentFactory]

private val LOG = Logger.getInstance("org.rust.lang.core.resolve.NameResolution")

fun processDotExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    processor: (DotExprResolveVariant) -> Boolean
): Boolean {
    if (processFieldExprResolveVariants(lookup, receiverType, processor)) return true
    if (processMethodDeclarationsWithDeref(lookup, receiverType) { processor(it) }) return true

    return false
}

fun processFieldExprResolveVariants(
    lookup: ImplLookup,
    receiverType: Ty,
    processor: (FieldResolveVariant) -> Boolean
): Boolean {
    for ((i, ty) in lookup.coercionSequence(receiverType).withIndex()) {
        if (ty !is TyAdt || ty.item !is RsStructItem) continue
        if (processFieldDeclarations(ty.item) { processor(FieldResolveVariant(it.name, it.element!!, ty, i)) }) return true
    }

    return false
}

fun processStructLiteralFieldResolveVariants(field: RsStructLiteralField, processor: RsResolveProcessor): Boolean {
    val resolved = field.parentStructLiteral.path.reference.deepResolve()
    val structOrEnumVariant = resolved as? RsFieldsOwner ?: return false
    return processFieldDeclarations(structOrEnumVariant, processor)
}

fun processMethodCallExprResolveVariants(lookup: ImplLookup, receiverType: Ty, processor: RsMethodResolveProcessor): Boolean =
    processMethodDeclarationsWithDeref(lookup, receiverType, processor)

/**
 * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
 *
 *  ```
 *  // foo.rs
 *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir
 *
 *  pub mod nested {
 *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs`
 *  }
 *
 *  ```
 *
 *  | A module without a body is loaded from an external file, by default with the same name as the module,
 *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
 *  | from a subdirectory path that mirrors the module hierarchy.
 *
 * Reference:
 *      https://github.com/rust-lang/rust/blob/master/src/doc/reference.md#modules
 */
fun processModDeclResolveVariants(modDecl: RsModDeclItem, processor: RsResolveProcessor): Boolean {
    val dir = modDecl.containingMod.ownedDirectory ?: return false

    val explicitPath = modDecl.pathAttribute
    if (explicitPath != null) {
        val vFile = dir.virtualFile.findFileByRelativePath(FileUtil.toSystemIndependentName(explicitPath))
            ?: return false
        val mod = vFile.toPsiFile(modDecl.project)?.rustFile ?: return false

        val name = modDecl.name ?: return false
        return processor(name, mod)
    }
    if (modDecl.isLocal) return false

    for (file in dir.files) {
        if (file == modDecl.contextualFile.originalFile || file.name == RsConstants.MOD_RS_FILE) continue
        val mod = file.rustFile ?: continue
        val fileName = FileUtil.getNameWithoutExtension(file.name)
        val modDeclName = modDecl.referenceName
        // Handle case-insensitive filesystem (windows)
        val name = if (modDeclName.toLowerCase() == fileName.toLowerCase()) {
            modDeclName
        } else {
            fileName
        }
        if (processor(name, mod)) return true
    }

    for (d in dir.subdirectories) {
        val mod = d.findFile(RsConstants.MOD_RS_FILE)?.rustFile ?: continue
        if (processor(d.name, mod)) return true
    }

    return false
}

fun processExternCrateResolveVariants(element: RsElement, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    val target = element.containingCargoTarget ?: return false
    val pkg = target.pkg

    val visitedDeps = mutableSetOf<String>()
    fun processPackage(pkg: CargoWorkspace.Package): Boolean {
        if (isCompletion && pkg.origin != PackageOrigin.DEPENDENCY) return false
        val libTarget = pkg.libTarget ?: return false
        // When crate depends on another version of the same crate
        // we shouldn't add current package into resolve/completion results
        if (otherVersionOfSameCrate.hitOnFalse(libTarget == target)) return false

        if (pkg.origin == PackageOrigin.STDLIB && pkg.name in visitedDeps) return false
        visitedDeps += pkg.name
        return processor.lazy(libTarget.normName) {
            libTarget.crateRoot?.toPsiFile(element.project)?.rustFile
        }
    }

    if (processPackage(pkg)) return true
    val explicitDepsFirst = pkg.dependencies.sortedBy {
        when (it.origin) {
            PackageOrigin.WORKSPACE,
            PackageOrigin.DEPENDENCY,
            PackageOrigin.TRANSITIVE_DEPENDENCY -> {
                NameResolutionTestmarks.shadowingStdCrates.hit()
                0
            }
            PackageOrigin.STDLIB -> 1
        }
    }
    for (p in explicitDepsFirst) {
        if (processPackage(p)) return true
    }
    return false
}

private val RsPath.qualifier: RsPath?
    get() {
        path?.let { return it }
        var ctx = context
        while (ctx is RsPath) {
            ctx = ctx.context
        }
        return (ctx as? RsUseSpeck)?.qualifier
    }

fun processPathResolveVariants(lookup: ImplLookup, path: RsPath, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {

    val qualifier = path.qualifier
    val typeQual = path.typeQual
    val parent = path.context
    val ns = when (parent) {
        is RsPath, is RsTypeElement, is RsTraitRef, is RsStructLiteral -> TYPES
        is RsUseSpeck -> if (parent.isStarImport) TYPES_N_MACROS else TYPES_N_VALUES_N_MACROS
        is RsPathExpr -> if (isCompletion) TYPES_N_VALUES else VALUES
        else -> TYPES_N_VALUES
    }
    val isEdition2018 = path.containingCargoTarget?.edition == CargoWorkspace.Edition.EDITION_2018

    if (qualifier != null) {
        val primitiveType = TyPrimitive.fromPath(qualifier)
        if (primitiveType != null) {
            val selfSubst = mapOf(TyTypeParameter.self() to primitiveType).toTypeSubst()
            if (processAssociatedItemsWithSelfSubst(lookup, primitiveType, ns, selfSubst, processor)) return true
        }

        val (base, subst) = qualifier.reference.advancedResolve() ?: return false
        if (base is RsMod) {
            val s = base.`super`
            if (s != null && processor("super", s)) return true

            val containingMod = path.containingMod
            if (Namespace.Macros in ns && base is RsFile && base.isCrateRoot &&
                containingMod is RsFile && containingMod.isCrateRoot &&
                containingMod.hasUseExternMacrosFeature) {
                if (processAll(exportedMacros(base), processor)) return true
            }
        }
        if (parent is RsUseSpeck && path.path == null) {
            selfInGroup.hit()
            if (processor("self", base)) return true
        }
        val isSuperChain = isSuperChain(qualifier)
        if (processItemOrEnumVariantDeclarations(base, ns, processor,
                withPrivateImports = isSuperChain,
                withPlainExternCrateItems = isSuperChain || !isEdition2018)) return true
        if (base is RsTypeDeclarationElement && parent !is RsUseSpeck) {
            // Foo::<Bar>::baz
            val selfTy = if (base is RsImplItem && qualifier.hasCself) {
                // impl S { fn foo() { Self::bar() } }
                base.typeReference?.type ?: TyUnknown
            } else {
                val realSubst = if (qualifier.typeArgumentList != null) {
                    // If the path contains explicit type arguments `Foo::<_, Bar, _>::baz`
                    // it means that all possible `TyInfer` has already substituted (with `_`)
                    subst
                } else {
                    subst.mapTypeValues { (_, v) -> v.foldTyTypeParameterWith { TyInfer.TyVar(it) } }
                }
                base.declaredType.substitute(realSubst)
            }
            val isSelf = qualifier.hasColonColon || !qualifier.hasCself
            val selfSubst = if (isSelf && base !is RsTraitItem) {
                mapOf(TyTypeParameter.self() to selfTy).toTypeSubst()
            } else {
                emptySubstitution
            }
            if (processAssociatedItemsWithSelfSubst(lookup, selfTy, ns, selfSubst, processor)) return true
        }
        return false
    }

    if (typeQual != null) {
        // <T as Trait>::Item
        val trait = typeQual.traitRef?.resolveToBoundTrait ?: return false
        val selfSubst = mapOf(TyTypeParameter.self() to typeQual.typeReference.type).toTypeSubst()
        val subst = trait.subst.substituteInValues(selfSubst) + selfSubst
        if (processAllWithSubst(trait.element.members?.typeAliasList.orEmpty(), subst, processor)) return true
        return false
    }

    if (isCompletion) {
        // Complete possible associated types in a case like `Trait</*caret*/>`
        val possibleTypeArgs = parent?.parent?.parent
        if (possibleTypeArgs is RsTypeArgumentList) {
            val trait = (possibleTypeArgs.parent as? RsPath)?.reference?.resolve() as? RsTraitItem
            if (trait != null && processAssocTypeVariants(trait, processor)) return true
        }
    }

    val containingMod = path.containingMod
    val crateRoot = path.crateRoot
    if (!path.hasColonColon) {
        run { // hacks around $crate macro metavar. See `expandDollarCrateVar` function docs
            val referenceName = path.referenceName
            if (referenceName.startsWith(MACRO_CRATE_IDENTIFIER_PREFIX) && path.findMacroCallExpandedFrom() != null) {
                val crate = referenceName.removePrefix(MACRO_CRATE_IDENTIFIER_PREFIX)
                val result = if (crate == "self") {
                    processor.lazy(referenceName) { path.crateRoot }
                } else {
                    processExternCrateResolveVariants(path, false) {
                        if (it.name == crate) processor.lazy(referenceName) { it.element } else false
                    }
                }
                if (result) return true
            }
        }
        if (Namespace.Types in ns) {
            if (processor("self", containingMod)) return true
            val superMod = containingMod.`super`
            if (superMod != null && processor("super", superMod)) return true
            if (crateRoot != null && processor("crate", crateRoot)) return true
            if (path.kind == PathKind.IDENTIFIER && isEdition2018) {
                val attributes = (crateRoot as? RsFile)?.attributes
                val implicitExternCrate = when (attributes) {
                    NONE -> "std"
                    NO_STD -> "core"
                    else -> null
                }
                // We shouldn't process implicit extern crate here
                // because we add it in `ItemResolutionKt.processItemDeclarations`
                if (path.referenceName != implicitExternCrate) {
                    if (processExternCrateResolveVariants(path, isCompletion, processor)) return true
                }
            }
        }
    }

    // Paths in use items are implicitly global.
    if (path.hasColonColon || path.contextStrict<RsUseItem>() != null) {
        if (crateRoot != null) {
            if (processItemOrEnumVariantDeclarations(crateRoot, ns, processor,
                    withPrivateImports = true,
                    withPlainExternCrateItems = !isEdition2018)) return true
        }
        return false
    }

    return processNestedScopesUpwards(path, processor, ns, !isEdition2018)
}

fun processPatBindingResolveVariants(binding: RsPatBinding, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    return processNestedScopesUpwards(binding, { entry ->
        processor.lazy(entry.name) {
            val element = entry.element ?: return@lazy null
            val isConstant = element.isConstantLike
            val isPathOrDestructable = when (element) {
                is RsMod, is RsEnumItem, is RsEnumVariant, is RsStructItem -> true
                else -> false
            }
            if (isConstant || (isCompletion && isPathOrDestructable)) element else null
        }
    }, if (isCompletion) TYPES_N_VALUES else VALUES)
}

fun processLabelResolveVariants(label: RsLabel, processor: RsResolveProcessor): Boolean {
    for (scope in label.ancestors) {
        if (scope is RsLambdaExpr || scope is RsFunction) return false
        if (scope is RsLabeledExpression) {
            val labelDecl = scope.labelDecl ?: continue
            if (processor(labelDecl)) return true
        }
    }
    return false
}

fun processLifetimeResolveVariants(lifetime: RsLifetime, processor: RsResolveProcessor): Boolean {
    if (lifetime.isPredefined) return false
    loop@ for (scope in lifetime.ancestors) {
        val lifetimeParameters = when (scope) {
            is RsGenericDeclaration -> scope.typeParameterList?.lifetimeParameterList
            is RsWhereClause -> scope.wherePredList.mapNotNull { it.forLifetimes }.flatMap { it.lifetimeParameterList }
            is RsForInType -> scope.forLifetimes.lifetimeParameterList
            is RsPolybound -> scope.forLifetimes?.lifetimeParameterList
            else -> continue@loop
        }
        if (processAll(lifetimeParameters.orEmpty(), processor)) return true
    }
    return false
}

fun processLocalVariables(place: RsElement, processor: (RsPatBinding) -> Unit) {
    walkUp(place, { it is RsItemElement }) { cameFrom, scope ->
        processLexicalDeclarations(scope, cameFrom, VALUES) { v ->
            val el = v.element
            if (el is RsPatBinding) processor(el)
            true
        }
    }
}

/**
 * Resolves an absolute path.
 */
fun resolveStringPath(path: String, workspace: CargoWorkspace, project: Project): Pair<RsNamedElement, CargoWorkspace.Package>? {
    check(!path.startsWith("::"))
    val parts = path.split("::", limit = 2)
    if (parts.size != 2) return null
    val pkg = workspace.findPackage(parts[0]) ?: run {
        return if (isUnitTestMode) {
            // Allows to set a fake path for some item in tests via
            // lang attribute, e.g. `#[lang = "std::iter::Iterator"]`
            RsLangItemIndex.findLangItem(project, path)?.let { it to workspace.packages.first() }
        } else {
            null
        }
    }

    val el = pkg.targets.asSequence()
        .mapNotNull { RsCodeFragmentFactory(project).createCrateRelativePath("::${parts[1]}", it) }
        .mapNotNull { it.reference.resolve() }
        .filterIsInstance<RsNamedElement>()
        .firstOrNull() ?: return null
    return el to pkg
}

fun processMacroReferenceVariants(ref: RsMacroReference, processor: RsResolveProcessor): Boolean {
    val definition = ref.ancestorStrict<RsMacroCase>() ?: return false
    val simple = definition.macroPattern.descendantsOfType<RsMacroBinding>()
        .toList()

    return simple.any { processor(it) }
}

fun processDeriveTraitResolveVariants(element: RsMetaItem, traitName: String, processor: RsResolveProcessor): Boolean {
    val traits = RsNamedElementIndex.findDerivableTraits(element.project, traitName)
    return processAll(traits, processor)
}

fun processMacroCallVariants(element: PsiElement, processor: RsResolveProcessor): Boolean {
    val result = walkUp(element, { it is RsMod && it.isCrateRoot }, false) { _, scope ->
        if (scope !is RsItemsOwner) return@walkUp false
        processAll(visibleMacros(scope), processor)
    }
    if (result) return true

    val prelude = (element.contextOrSelf<RsElement>())?.findDependencyCrateRoot(STD) ?: return false
    return processAll(exportedMacros(prelude), processor)
}

fun processBinaryOpVariants(element: RsBinaryOp, operator: OverloadableBinaryOperator,
                            processor: RsResolveProcessor): Boolean {
    val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return false
    val rhsType = binaryExpr.right?.type ?: return false
    val lhsType = binaryExpr.left.type
    val lookup = ImplLookup.relativeTo(element)
    val function = lookup.findOverloadedOpImpl(lhsType, rhsType, operator)
        ?.members
        ?.functionList
        ?.find { it.name == operator.fnName }
        ?: return false
    return processor(function)
}

fun processAssocTypeVariants(element: RsAssocTypeBinding, processor: RsResolveProcessor): Boolean {
    val trait = element.parentPath?.reference?.resolve() as? RsTraitItem ?: return false
    return processAssocTypeVariants(trait, processor)
}

fun processAssocTypeVariants(trait: RsTraitItem, processor: RsResolveProcessor): Boolean {
    if (trait.associatedTypesTransitively.any { processor(it) }) return true
    return false
}

private fun visibleMacros(scope: RsItemsOwner): List<RsMacro> =
    CachedValuesManager.getCachedValue(scope) {
        val macros = visibleMacrosInternal(scope)
        CachedValueProvider.Result.create(macros, PsiModificationTracker.MODIFICATION_COUNT)
    }

private fun visibleMacrosInternal(scope: RsItemsOwner): List<RsMacro> {
    val hasExternMacrosFeature = lazy { (scope.crateRoot as? RsFile)?.hasUseExternMacrosFeature ?: false }
    val visibleMacros = mutableListOf<RsMacro>()

    val exportingMacrosCrates = mutableMapOf<String, RsFile>()
    val useItems = mutableListOf<RsUseItem>()

    loop@ for (item in scope.itemsAndMacros) {
        when (item) {
            is RsMacro -> visibleMacros += item
            is RsModItem ->
                if (missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    visibleMacros += visibleMacros(item)
                }
            is RsModDeclItem ->
                if (missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    val mod = item.reference.resolve() as? RsMod ?: continue@loop
                    visibleMacros += visibleMacros(mod)
                }
            is RsExternCrateItem -> {
                val mod = item.reference.resolve() as? RsFile ?: continue@loop
                if (missingMacroUse.hitOnFalse(item.hasMacroUse)) {
                    // If extern crate has `#[macro_use]` attribute
                    // we can use all exported macros from the corresponding crate
                    visibleMacros += exportedMacros(mod)
                } else {
                    // otherwise we can use only reexported macros
                    val reexportedMacros = reexportedMacros(item)
                    if (reexportedMacros != null) {
                        // via #[macro_reexport] attribute (old way)
                        visibleMacros += reexportedMacros
                    } else {
                        // or from `use` items (new way).
                        // It requires `#![feature(use_extern_macros)]`
                        if (hasExternMacrosFeature.value) {
                            exportingMacrosCrates[item.nameWithAlias] = mod
                        }
                    }
                }


            }
            is RsUseItem -> useItems += item
        }
    }

    // Really we are not very accurate here because we use extern crate from same parent
    // Ideally we should take into account all extern crate from super mods
    // but it's ok for now
    if (exportingMacrosCrates.isNotEmpty()) {
        for (useItem in useItems) {
            visibleMacros += collectExportedMacros(useItem, exportingMacrosCrates)
        }
    }

    return visibleMacros
}

private fun exportedMacros(scope: RsFile): List<RsMacro> {
    if (!scope.isCrateRoot) {
        LOG.warn("`${scope.virtualFile}` should be crate root")
        return emptyList()
    }
    return CachedValuesManager.getCachedValue(scope) {
        val macros = exportedMacrosInternal(scope)
        CachedValueProvider.Result.create(macros, scope.project.rustStructureModificationTracker)
    }
}

private fun exportedMacrosInternal(scope: RsFile): List<RsMacro> {
    val allExportedMacros = RsMacroIndex.allExportedMacros(scope.project)
    return buildList {
        addAll(allExportedMacros[scope].orEmpty())

        val exportingMacrosCrates = mutableMapOf<String, RsMod>()

        val externCrates = scope.stubChildrenOfType<RsExternCrateItem>()
        for (item in externCrates) {
            val reexportedMacros = reexportedMacros(item)
            if (reexportedMacros != null) {
                addAll(reexportedMacros)
            } else {
                exportingMacrosCrates[item.nameWithAlias] = item.reference.resolve() as? RsMod ?: continue
            }
        }

        val implicitStdlibCrate = implicitStdlibCrate(scope)
        if (implicitStdlibCrate != null) {
            exportingMacrosCrates[implicitStdlibCrate.name] = implicitStdlibCrate.crateRoot
        }

        if (exportingMacrosCrates.isNotEmpty()) {
            for (useItem in scope.stubChildrenOfType<RsUseItem>()) {
                // only public use items can reexport macros
                if (!useItem.isPublic) continue
                addAll(collectExportedMacros(useItem, exportingMacrosCrates))
            }
        }
    }
}

/**
 * Returns list of re-exported macros via `[macro_reexport]` attribute from given extern crate
 * or null if extern crate item doesn't have `[macro_reexport]` attribute
 */
private fun reexportedMacros(item: RsExternCrateItem): List<RsMacro>? {
    val macroReexportAttr = item.findOuterAttr("macro_reexport") ?: return null
    val exportingMacroNames = macroReexportAttr
        .metaItem
        .metaItemArgs
        ?.metaItemList
        ?.mapNotNull { it.name }
        ?: return emptyList()
    val mod = item.reference.resolve() as? RsFile ?: return emptyList()
    val nameToExportedMacro = exportedMacros(mod).mapNotNull {
        val name = it.name ?: return@mapNotNull null
        name to it
    }.toMap()
    return exportingMacroNames.mapNotNull { nameToExportedMacro[it] }
}

private fun collectExportedMacros(
    useItem: RsUseItem,
    exportingMacrosCrates: Map<String, RsMod>
): List<RsMacro> {
    return buildList {
        val root = useItem.useSpeck ?: return@buildList

        fun forEachRootUseSpeck(useSpeck: RsUseSpeck, consumer: (RsUseSpeck, RsPath) -> Unit) {
            val path = useSpeck.path
            if (path != null) {
                consumer(useSpeck, path)
            } else {
                useSpeck.useGroup?.useSpeckList?.forEach { forEachRootUseSpeck(it, consumer) }
            }
        }

        forEachRootUseSpeck(root) { speck, path ->
            val basePath = path.basePath()
            // If path name is not in `exportingMacrosCrates` then
            // it can't be resolved to extern crate mod exporting macros.
            // Note, it significantly improves performance of macro resolution
            val expectedMod = exportingMacrosCrates[basePath.referenceName] ?: return@forEachRootUseSpeck

            val mod = basePath.reference.resolve() as? RsFile ?: return@forEachRootUseSpeck
            // Don't try to resolve leaf items if `mod` can't bring macros at all
            if (mod == expectedMod) {
                val crateExportedMacros = lazy { exportedMacros(mod) }

                speck.forEachLeafSpeck { leafSpeck ->
                    // We should use multi resolve here
                    // because single use speck can import module and macro at same time
                    val resolvedItems = leafSpeck.path?.reference?.multiResolve().orEmpty()

                    if (leafSpeck.isStarImport) {
                        if (mod == resolvedItems.singleOrNull()) {
                            addAll(crateExportedMacros.value)
                        }
                    } else {
                        val exportedMacros = resolvedItems.filterIsInstance<RsMacro>()
                            .filter { it in crateExportedMacros.value }
                        addAll(exportedMacros)
                    }
                }
            }
        }
    }
}

private fun processFieldDeclarations(struct: RsFieldsOwner, processor: RsResolveProcessor): Boolean {
    if (processAll(struct.namedFields, processor)) return true

    for ((idx, field) in struct.positionalFields.withIndex()) {
        if (processor(idx.toString(), field)) return true
    }
    return false
}

private fun processMethodDeclarationsWithDeref(lookup: ImplLookup, receiver: Ty, processor: RsMethodResolveProcessor): Boolean {
    return lookup.coercionSequence(receiver).withIndex().any { (i, ty) ->
        val methodProcessor: (AssocItemScopeEntry) -> Boolean = { (name, element, _, source) ->
            element is RsFunction && !element.isAssocFn && processor(MethodResolveVariant(name, element, ty, i, source))
        }
        processAssociatedItems(lookup, ty, VALUES, methodProcessor)
    }
}

private fun processAssociatedItems(
    lookup: ImplLookup,
    type: Ty,
    ns: Set<Namespace>,
    processor: (AssocItemScopeEntry) -> Boolean
): Boolean {
    val traitBounds = (type as? TyTypeParameter)?.getTraitBoundsTransitively()
    val visitedInherent = mutableSetOf<String>()
    fun processTraitOrImpl(traitOrImpl: TraitImplSource, inherent: Boolean): Boolean {
        fun inherentProcessor(entry: RsNamedElement): Boolean {
            val name = entry.name ?: return false
            if (inherent) visitedInherent.add(name)
            if (!inherent && name in visitedInherent) return false

            val subst = if (traitBounds != null && traitOrImpl is TraitImplSource.TraitBound) {
                // Retrieve trait subst for associated type like
                // trait SliceIndex<T> { type Output; }
                // fn get<I: : SliceIndex<S>>(index: I) -> I::Output
                // Resulting subst will contains mapping T => S
                traitBounds.filter { it.element == traitOrImpl.value }.map { it.subst }
            } else {
                listOf(emptySubstitution)
            }
            return subst.any { processor(AssocItemScopeEntry(name, entry, it, traitOrImpl)) }
        }

        /**
         * For `impl T for Foo`, this'll walk impl members and trait `T` members,
         * which are not implemented.
         */
        fun processMembersWithDefaults(accessor: (RsMembers) -> List<RsNamedElement>): Boolean {
            val directlyImplemented = traitOrImpl.value.members?.let { accessor(it) }.orEmpty()
            if (directlyImplemented.any { inherentProcessor(it) }) return true

            if (traitOrImpl is TraitImplSource.ExplicitImpl) {
                val direct = directlyImplemented.map { it.name }.toSet()
                val membersFromTrait = traitOrImpl.value.implementedTrait?.element?.members ?: return false
                for (member in accessor(membersFromTrait)) {
                    if (member.name !in direct && inherentProcessor(member)) return true
                }
            }

            return false
        }

        if (Namespace.Values in ns) {
            if (processMembersWithDefaults { it.expandedMembers.functionsAndConstants }) return true
        }
        if (Namespace.Types in ns) {
            if (processMembersWithDefaults { it.expandedMembers.types }) return true
        }
        return false
    }

    val (inherent, traits) = lookup.findImplsAndTraits(type).partition { it is TraitImplSource.ExplicitImpl && it.value.traitRef == null }
    if (inherent.any { processTraitOrImpl(it, true) }) return true
    if (traits.any { processTraitOrImpl(it, false) }) return true
    return false
}

private fun processAssociatedItemsWithSelfSubst(
    lookup: ImplLookup,
    type: Ty,
    ns: Set<Namespace>,
    selfSubst: Substitution,
    processor: RsResolveProcessor
): Boolean {
    return processAssociatedItems(lookup, type, ns) {
        processor(it.copy(subst = it.subst + selfSubst))
    }
}

private fun processLexicalDeclarations(
    scope: RsElement,
    cameFrom: PsiElement,
    ns: Set<Namespace>,
    withPlainExternCrateItems: Boolean = true,
    processor: RsResolveProcessor
): Boolean {
    check(cameFrom.context == scope)

    fun processPattern(pattern: RsPat, processor: RsResolveProcessor): Boolean {
        val boundNames = PsiTreeUtil.findChildrenOfType(pattern, RsPatBinding::class.java)
            .filter { it.reference.resolve() == null }
        return processAll(boundNames, processor)
    }

    fun processCondition(condition: RsCondition?, processor: RsResolveProcessor): Boolean {
        if (condition == null || condition == cameFrom) return false
        val pat = condition.pat
        if (pat != null && processPattern(pat, processor)) return true
        return false
    }

    when (scope) {
        is RsMod -> {
            if (processItemDeclarations(scope, ns, processor,
                    withPrivateImports = true,
                    withPlainExternCrateItems = withPlainExternCrateItems)) return true
        }

        is RsStructItem,
        is RsEnumItem,
        is RsTypeAlias -> {
            scope as RsGenericDeclaration
            if (processAll(scope.typeParameters, processor)) return true
        }

        is RsTraitOrImpl -> {
            if (processAll(scope.typeParameters, processor)) return true
            if (processor("Self", scope)) return true
        }

        is RsFunction -> {
            if (Namespace.Types in ns) {
                if (processAll(scope.typeParameters, processor)) return true
            }
            if (Namespace.Values in ns) {
                val selfParam = scope.selfParameter
                if (selfParam != null && processor("self", selfParam)) return true

                for (parameter in scope.valueParameters) {
                    val pat = parameter.pat ?: continue
                    if (processPattern(pat, processor)) return true
                }
            }
        }

        is RsBlock -> {
            // We want to filter out
            // all non strictly preceding let declarations.
            //
            // ```
            // let x = 92; // visible
            // let x = x;  // not visible
            //         ^ context.place
            // let x = 62; // not visible
            // ```
            val visited = mutableSetOf<String>()
            if (Namespace.Values in ns) {
                val shadowingProcessor = { e: ScopeEntry ->
                    (e.name !in visited) && run {
                        visited += e.name
                        processor(e)
                    }
                }

                for (stmt in scope.stmtList.asReversed()) {
                    val pat = (stmt as? RsLetDecl)?.pat ?: continue
                    if (PsiUtilCore.compareElementsByPosition(cameFrom, stmt) < 0) continue
                    if (stmt == cameFrom) continue
                    if (processPattern(pat, shadowingProcessor)) return true
                }
            }

            return processItemDeclarations(scope, ns, processor, withPrivateImports = true)
        }

        is RsForExpr -> {
            if (scope.expr == cameFrom) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.pat
            if (pat != null && processPattern(pat, processor)) return true
        }

        is RsIfExpr -> {
            if (Namespace.Values !in ns) return false
            // else branch of 'if let' expression shouldn't look into condition pattern
            if (scope.elseBranch == cameFrom) return false
            return processCondition(scope.condition, processor)
        }
        is RsWhileExpr -> {
            if (Namespace.Values !in ns) return false
            return processCondition(scope.condition, processor)
        }

        is RsLambdaExpr -> {
            if (Namespace.Values !in ns) return false
            for (parameter in scope.valueParameterList.valueParameterList) {
                val pat = parameter.pat
                if (pat != null && processPattern(pat, processor)) return true
            }
        }

        is RsMatchArm -> {
            // Rust allows to defined several patterns in the single match arm,
            // but they all must bind the same variables, hence we can inspect
            // only the first one.
            if (cameFrom in scope.patList) return false
            if (Namespace.Values !in ns) return false
            val pat = scope.patList.firstOrNull()
            if (pat != null && processPattern(pat, processor)) return true

        }
    }
    return false
}

fun processNestedScopesUpwards(
    scopeStart: RsElement,
    processor: RsResolveProcessor,
    ns: Set<Namespace>,
    withPlainExternCrateItems: Boolean = true
): Boolean {
    val prevScope = mutableSetOf<String>()
    if (walkUp(scopeStart, { it is RsMod }) { cameFrom, scope ->
        val currScope = mutableListOf<String>()
        val shadowingProcessor = { e: ScopeEntry ->
            e.name !in prevScope && run {
                currScope += e.name
                processor(e)
            }
        }
        if (processLexicalDeclarations(scope, cameFrom, ns, withPlainExternCrateItems, shadowingProcessor)) return@walkUp true
        prevScope.addAll(currScope)
        false
    }) {
        return true
    }

    val prelude = findPrelude(scopeStart)
    val preludeProcessor: (ScopeEntry) -> Boolean = { v -> v.name !in prevScope && processor(v) }
    if (prelude != null && processItemDeclarations(prelude, ns, preludeProcessor,
            withPrivateImports = false,
            withPlainExternCrateItems = withPlainExternCrateItems)) return true

    return false
}

private fun findPrelude(element: RsElement): RsFile? {
    val crateRoot = element.crateRoot as? RsFile ?: return null
    val cargoPackage = crateRoot.containingCargoPackage
    val isStdlib = cargoPackage?.origin == PackageOrigin.STDLIB
    val packageName = cargoPackage?.normName

    // `std` and `core` crates explicitly add their prelude
    val stdlibCrateRoot = if (isStdlib && (packageName == STD || packageName == CORE)) {
        crateRoot
    } else {
        implicitStdlibCrate(crateRoot)?.crateRoot
    }

    return stdlibCrateRoot
        ?.virtualFile
        ?.findFileByRelativePath("../prelude/v1.rs")
        ?.toPsiFile(element.project)
        ?.rustFile
}

// Implicit extern crate from stdlib
private fun implicitStdlibCrate(scope: RsFile): ImplicitStdlibCrate? {
    val (name, crateRoot) = when (scope.attributes) {
        NO_CORE -> return null
        NO_STD -> CORE to scope.findDependencyCrateRoot(CORE)
        NONE -> STD to scope.findDependencyCrateRoot(STD)
    }
    return if (crateRoot == null) null else ImplicitStdlibCrate(name, crateRoot)
}

// There's already similar functions in TreeUtils, should use it
private fun walkUp(
    start: PsiElement,
    stopAfter: (RsElement) -> Boolean,
    stopAtFileBoundary: Boolean = true,
    processor: (cameFrom: PsiElement, scope: RsElement) -> Boolean
): Boolean {
    var cameFrom = start
    var scope = start.context as RsElement?
    while (scope != null) {
        ProgressManager.checkCanceled()
        if (processor(cameFrom, scope)) return true
        if (stopAfter(scope)) break
        cameFrom = scope
        scope = scope.context as? RsElement
        if (!stopAtFileBoundary) {
            if (scope == null && cameFrom is RsFile) {
                scope = cameFrom.`super`
            }
        }
    }
    return false
}

fun isSuperChain(path: RsPath): Boolean {
    val qual = path.path
    return (path.referenceName == "super" || path.referenceName == "self") && (qual == null || isSuperChain(qual))
}


object NameResolutionTestmarks {
    val shadowingStdCrates = Testmark("shadowingStdCrates")
    val missingMacroExport = Testmark("missingMacroExport")
    val missingMacroUse = Testmark("missingMacroUse")
    val selfInGroup = Testmark("selfInGroup")
    val otherVersionOfSameCrate = Testmark("otherVersionOfSameCrate")
}

private data class ImplicitStdlibCrate(val name: String, val crateRoot: RsFile)
