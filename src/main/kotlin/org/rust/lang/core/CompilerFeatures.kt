/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.lang.core

import com.intellij.util.text.SemVer
import org.rust.lang.core.FeatureState.*

val ASM = CompilerFeature("asm", ACTIVE, "1.0.0")
val CONCAT_IDENTS = CompilerFeature("concat_idents", ACTIVE, "1.0.0")
val LINK_ARGS = CompilerFeature("link_args", ACTIVE, "1.0.0")
val LOG_SYNTAX = CompilerFeature("log_syntax", ACTIVE, "1.0.0")
val NON_ASCII_IDENTS = CompilerFeature("non_ascii_idents", ACTIVE, "1.0.0")
val PLUGIN_REGISTRAR = CompilerFeature("plugin_registrar", ACTIVE, "1.0.0")
val THREAD_LOCAL = CompilerFeature("thread_local", ACTIVE, "1.0.0")
val TRACE_MACROS = CompilerFeature("trace_macros", ACTIVE, "1.0.0")
// rustc internal, for now:
val INTRINSICS = CompilerFeature("intrinsics", ACTIVE, "1.0.0")
val LANG_ITEMS = CompilerFeature("lang_items", ACTIVE, "1.0.0")
val LINK_LLVM_INTRINSICS = CompilerFeature("link_llvm_intrinsics", ACTIVE, "1.0.0")
val LINKAGE = CompilerFeature("linkage", ACTIVE, "1.0.0")
val QUOTE = CompilerFeature("quote", ACTIVE, "1.0.0")
// rustc internal
val RUSTC_DIAGNOSTIC_MACROS = CompilerFeature("rustc_diagnostic_macros", ACTIVE, "1.0.0")
val RUSTC_CONST_UNSTABLE = CompilerFeature("rustc_const_unstable", ACTIVE, "1.0.0")
val BOX_SYNTAX = CompilerFeature("box_syntax", ACTIVE, "1.0.0")
val UNBOXED_CLOSURES = CompilerFeature("unboxed_closures", ACTIVE, "1.0.0")
val FUNDAMENTAL = CompilerFeature("fundamental", ACTIVE, "1.0.0")
val MAIN = CompilerFeature("main", ACTIVE, "1.0.0")
val NEEDS_ALLOCATOR = CompilerFeature("needs_allocator", ACTIVE, "1.4.0")
val ON_UNIMPLEMENTED = CompilerFeature("on_unimplemented", ACTIVE, "1.0.0")
val PLUGIN = CompilerFeature("plugin", ACTIVE, "1.0.0")
val SIMD_FFI = CompilerFeature("simd_ffi", ACTIVE, "1.0.0")
val START = CompilerFeature("start", ACTIVE, "1.0.0")
val STRUCTURAL_MATCH = CompilerFeature("structural_match", ACTIVE, "1.8.0")
val PANIC_RUNTIME = CompilerFeature("panic_runtime", ACTIVE, "1.10.0")
val NEEDS_PANIC_RUNTIME = CompilerFeature("needs_panic_runtime", ACTIVE, "1.10.0")
// OIBIT specific features
val OPTIN_BUILTIN_TRAITS = CompilerFeature("optin_builtin_traits", ACTIVE, "1.0.0")
// Allows use of #[staged_api]
// rustc internal
val STAGED_API = CompilerFeature("staged_api", ACTIVE, "1.0.0")
// Allows using #![no_core]
val NO_CORE = CompilerFeature("no_core", ACTIVE, "1.3.0")
// Allows using `box` in patterns; RFC 469
val BOX_PATTERNS = CompilerFeature("box_patterns", ACTIVE, "1.0.0")
// Allows using the unsafe_destructor_blind_to_params attribute;
// RFC 1238
val DROPCK_PARAMETRICITY = CompilerFeature("dropck_parametricity", ACTIVE, "1.3.0")
// Allows using the may_dangle attribute; RFC 1327
val DROPCK_EYEPATCH = CompilerFeature("dropck_eyepatch", ACTIVE, "1.10.0")
// Allows the use of custom attributes; RFC 572
val CUSTOM_ATTRIBUTE = CompilerFeature("custom_attribute", ACTIVE, "1.0.0")
// Allows the use of #[derive(Anything)] as sugar for
// #[derive_Anything].
val CUSTOM_DERIVE = CompilerFeature("custom_derive", ACTIVE, "1.0.0")
// Allows the use of rustc_* attributes; RFC 572
val RUSTC_ATTRS = CompilerFeature("rustc_attrs", ACTIVE, "1.0.0")
// Allows the use of non lexical lifetimes; RFC 2094
val NLL = CompilerFeature("nll", ACTIVE, "1.0.0")
// Allows the use of #[allow_internal_unstable]. This is an
// attribute on macro_rules! and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
//
// rustc internal
val ALLOW_INTERNAL_UNSTABLE = CompilerFeature("allow_internal_unstable", ACTIVE, "1.0.0")
// Allows the use of #[allow_internal_unsafe]. This is an
// attribute on macro_rules! and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
//
// rustc internal
val ALLOW_INTERNAL_UNSAFE = CompilerFeature("allow_internal_unsafe", ACTIVE, "1.0.0")
// #23121. Array patterns have some hazards yet.
val SLICE_PATTERNS = CompilerFeature("slice_patterns", ACTIVE, "1.0.0")
// Allows the definition of `const fn` functions.
val CONST_FN = CompilerFeature("const_fn", ACTIVE, "1.2.0")
// Allows let bindings and destructuring in `const fn` functions and constants.
val CONST_LET = CompilerFeature("const_let", ACTIVE, "1.22.1")
// Allows using #[prelude_import] on glob `use` items.
//
// rustc internal
val PRELUDE_IMPORT = CompilerFeature("prelude_import", ACTIVE, "1.2.0")
// Allows default type parameters to influence type inference.
val DEFAULT_TYPE_PARAMETER_FALLBACK = CompilerFeature("default_type_parameter_fallback", ACTIVE, "1.3.0")
// Allows associated type defaults
val ASSOCIATED_TYPE_DEFAULTS = CompilerFeature("associated_type_defaults", ACTIVE, "1.2.0")
// allow `repr(simd)`, and importing the various simd intrinsics
val REPR_SIMD = CompilerFeature("repr_simd", ACTIVE, "1.4.0")
// allow `extern "platform-intrinsic" { ... }`
val PLATFORM_INTRINSICS = CompilerFeature("platform_intrinsics", ACTIVE, "1.4.0")
// allow `#[unwind(..)]`
// rustc internal for rust runtime
val UNWIND_ATTRIBUTES = CompilerFeature("unwind_attributes", ACTIVE, "1.4.0")
// allow the use of `#[naked]` on functions.
val NAKED_FUNCTIONS = CompilerFeature("naked_functions", ACTIVE, "1.9.0")
// allow `#[no_debug]`
val NO_DEBUG = CompilerFeature("no_debug", ACTIVE, "1.5.0")
// allow `#[omit_gdb_pretty_printer_section]`
// rustc internal.
val OMIT_GDB_PRETTY_PRINTER_SECTION = CompilerFeature("omit_gdb_pretty_printer_section", ACTIVE, "1.5.0")
// Allows cfg(target_vendor = "...").
val CFG_TARGET_VENDOR = CompilerFeature("cfg_target_vendor", ACTIVE, "1.5.0")
// Allow attributes on expressions and non-item statements
val STMT_EXPR_ATTRIBUTES = CompilerFeature("stmt_expr_attributes", ACTIVE, "1.6.0")
// allow using type ascription in expressions
val TYPE_ASCRIPTION = CompilerFeature("type_ascription", ACTIVE, "1.6.0")
// Allows cfg(target_thread_local)
val CFG_TARGET_THREAD_LOCAL = CompilerFeature("cfg_target_thread_local", ACTIVE, "1.7.0")
// rustc internal
val ABI_VECTORCALL = CompilerFeature("abi_vectorcall", ACTIVE, "1.7.0")
// X..Y patterns
val EXCLUSIVE_RANGE_PATTERN = CompilerFeature("exclusive_range_pattern", ACTIVE, "1.11.0")
// impl specialization (RFC 1210)
val SPECIALIZATION = CompilerFeature("specialization", ACTIVE, "1.7.0")
// Allows cfg(target_has_atomic = "...").
val CFG_TARGET_HAS_ATOMIC = CompilerFeature("cfg_target_has_atomic", ACTIVE, "1.9.0")
// The `!` type. Does not imply exhaustive_patterns (below) any more.
val NEVER_TYPE = CompilerFeature("never_type", ACTIVE, "1.13.0")
// Allows exhaustive pattern matching on types that contain uninhabited types.
val EXHAUSTIVE_PATTERNS = CompilerFeature("exhaustive_patterns", ACTIVE, "1.13.0")
// Allows all literals in attribute lists and values of key-value pairs.
val ATTR_LITERALS = CompilerFeature("attr_literals", ACTIVE, "1.13.0")
// Allows untagged unions `union U { ... }`
val UNTAGGED_UNIONS = CompilerFeature("untagged_unions", ACTIVE, "1.13.0")
// Used to identify the `compiler_builtins` crate
// rustc internal
val COMPILER_BUILTINS = CompilerFeature("compiler_builtins", ACTIVE, "1.13.0")
// Allows #[link(..., cfg(..))]
val LINK_CFG = CompilerFeature("link_cfg", ACTIVE, "1.14.0")
val USE_EXTERN_MACROS = CompilerFeature("use_extern_macros", ACTIVE, "1.15.0")
// `extern "ptx-*" fn()`
val ABI_PTX = CompilerFeature("abi_ptx", ACTIVE, "1.15.0")
// The `repr(i128)` annotation for enums
val REPR128 = CompilerFeature("repr128", ACTIVE, "1.16.0")
// The `unadjusted` ABI. Perma unstable.
// rustc internal
val ABI_UNADJUSTED = CompilerFeature("abi_unadjusted", ACTIVE, "1.16.0")
// Procedural macros 2.0.
val PROC_MACRO = CompilerFeature("proc_macro", ACTIVE, "1.16.0")
// Declarative macros 2.0 (`macro`).
val DECL_MACRO = CompilerFeature("decl_macro", ACTIVE, "1.17.0")
// Allows #[link(kind="static-nobundle"...)]
val STATIC_NOBUNDLE = CompilerFeature("static_nobundle", ACTIVE, "1.16.0")
// `extern "msp430-interrupt" fn()`
val ABI_MSP430_INTERRUPT = CompilerFeature("abi_msp430_interrupt", ACTIVE, "1.16.0")
// Used to identify crates that contain sanitizer runtimes
// rustc internal
val SANITIZER_RUNTIME = CompilerFeature("sanitizer_runtime", ACTIVE, "1.17.0")
// Used to identify crates that contain the profiler runtime
// rustc internal
val PROFILER_RUNTIME = CompilerFeature("profiler_runtime", ACTIVE, "1.18.0")
// `extern "x86-interrupt" fn()`
val ABI_X86_INTERRUPT = CompilerFeature("abi_x86_interrupt", ACTIVE, "1.17.0")
// Allows the `catch {...}` expression
val CATCH_EXPR = CompilerFeature("catch_expr", ACTIVE, "1.17.0")
// Used to preserve symbols (see llvm.used)
val USED = CompilerFeature("used", ACTIVE, "1.18.0")
// Allows module-level inline assembly by way of global_asm!()
val GLOBAL_ASM = CompilerFeature("global_asm", ACTIVE, "1.18.0")
// Allows overlapping impls of marker traits
val OVERLAPPING_MARKER_TRAITS = CompilerFeature("overlapping_marker_traits", ACTIVE, "1.18.0")
// Allows use of the :vis macro fragment specifier
val MACRO_VIS_MATCHER = CompilerFeature("macro_vis_matcher", ACTIVE, "1.18.0")
// rustc internal
val ABI_THISCALL = CompilerFeature("abi_thiscall", ACTIVE, "1.19.0")
// Allows a test to fail without failing the whole suite
val ALLOW_FAIL = CompilerFeature("allow_fail", ACTIVE, "1.19.0")
// Allows unsized tuple coercion.
val UNSIZED_TUPLE_COERCION = CompilerFeature("unsized_tuple_coercion", ACTIVE, "1.20.0")
// Generators
val GENERATORS = CompilerFeature("generators", ACTIVE, "1.21.0")
// Trait aliases
val TRAIT_ALIAS = CompilerFeature("trait_alias", ACTIVE, "1.24.0")
// rustc internal
val ALLOCATOR_INTERNALS = CompilerFeature("allocator_internals", ACTIVE, "1.20.0")
// #[doc(cfg(...))]
val DOC_CFG = CompilerFeature("doc_cfg", ACTIVE, "1.21.0")
// #[doc(masked)]
val DOC_MASKED = CompilerFeature("doc_masked", ACTIVE, "1.21.0")
// #[doc(spotlight)]
val DOC_SPOTLIGHT = CompilerFeature("doc_spotlight", ACTIVE, "1.22.0")
// #[doc(include="some-file")]
val EXTERNAL_DOC = CompilerFeature("external_doc", ACTIVE, "1.22.0")
// Future-proofing enums/structs with #[non_exhaustive] attribute (RFC 2008)
val NON_EXHAUSTIVE = CompilerFeature("non_exhaustive", ACTIVE, "1.22.0")
// `crate` as visibility modifier, synonymous to `pub(crate)`
val CRATE_VISIBILITY_MODIFIER = CompilerFeature("crate_visibility_modifier", ACTIVE, "1.23.0")
// extern types
val EXTERN_TYPES = CompilerFeature("extern_types", ACTIVE, "1.23.0")
// Allow trait methods with arbitrary self types
val ARBITRARY_SELF_TYPES = CompilerFeature("arbitrary_self_types", ACTIVE, "1.23.0")
// `crate` in paths
val CRATE_IN_PATHS = CompilerFeature("crate_in_paths", ACTIVE, "1.23.0")
// In-band lifetime bindings (e.g. `fn foo(x: &'a u8) -> &'a u8`)
val IN_BAND_LIFETIMES = CompilerFeature("in_band_lifetimes", ACTIVE, "1.23.0")
// generic associated types (RFC 1598)
val GENERIC_ASSOCIATED_TYPES = CompilerFeature("generic_associated_types", ACTIVE, "1.23.0")
// Resolve absolute paths as paths from other crates
val EXTERN_ABSOLUTE_PATHS = CompilerFeature("extern_absolute_paths", ACTIVE, "1.24.0")
// `foo.rs` as an alternative to `foo/mod.rs`
val NON_MODRS_MODS = CompilerFeature("non_modrs_mods", ACTIVE, "1.24.0")
// `extern` in paths
val EXTERN_IN_PATHS = CompilerFeature("extern_in_paths", ACTIVE, "1.23.0")
// Use `?` as the Kleene "at most one" operator
val MACRO_AT_MOST_ONCE_REP = CompilerFeature("macro_at_most_once_rep", ACTIVE, "1.25.0")
// Infer outlives requirements; RFC 2093
val INFER_OUTLIVES_REQUIREMENTS = CompilerFeature("infer_outlives_requirements", ACTIVE, "1.26.0")
// Multiple patterns with `|` in `if let` and `while let`
val IF_WHILE_OR_PATTERNS = CompilerFeature("if_while_or_patterns", ACTIVE, "1.26.0")
// Parentheses in patterns
val PATTERN_PARENTHESES = CompilerFeature("pattern_parentheses", ACTIVE, "1.26.0")
// Allows `#[repr(packed)]` attribute on structs
val REPR_PACKED = CompilerFeature("repr_packed", ACTIVE, "1.26.0")
// `use path as _;` and `extern crate c as _;`
val UNDERSCORE_IMPORTS = CompilerFeature("underscore_imports", ACTIVE, "1.26.0")
// The #[wasm_custom_section] attribute
val WASM_CUSTOM_SECTION = CompilerFeature("wasm_custom_section", ACTIVE, "1.26.0")
// Allows keywords to be escaped for use as identifiers
val RAW_IDENTIFIERS = CompilerFeature("raw_identifiers", ACTIVE, "1.26.0")
// Allows macro invocations in `extern {}` blocks
val MACROS_IN_EXTERN = CompilerFeature("macros_in_extern", ACTIVE, "1.27.0")
// `existential type`
val EXISTENTIAL_TYPE = CompilerFeature("existential_type", ACTIVE, "1.28.0")
// unstable #[target_feature] directives
val ARM_TARGET_FEATURE = CompilerFeature("arm_target_feature", ACTIVE, "1.27.0")
val AARCH64_TARGET_FEATURE = CompilerFeature("aarch64_target_feature", ACTIVE, "1.27.0")
val HEXAGON_TARGET_FEATURE = CompilerFeature("hexagon_target_feature", ACTIVE, "1.27.0")
val POWERPC_TARGET_FEATURE = CompilerFeature("powerpc_target_feature", ACTIVE, "1.27.0")
val MIPS_TARGET_FEATURE = CompilerFeature("mips_target_feature", ACTIVE, "1.27.0")
val AVX512_TARGET_FEATURE = CompilerFeature("avx512_target_feature", ACTIVE, "1.27.0")
val MMX_TARGET_FEATURE = CompilerFeature("mmx_target_feature", ACTIVE, "1.27.0")
val SSE4A_TARGET_FEATURE = CompilerFeature("sse4a_target_feature", ACTIVE, "1.27.0")
val TBM_TARGET_FEATURE = CompilerFeature("tbm_target_feature", ACTIVE, "1.27.0")
// Allows macro invocations of the form `#[foo::bar]`
val PROC_MACRO_PATH_INVOC = CompilerFeature("proc_macro_path_invoc", ACTIVE, "1.27.0")
// Allows macro invocations on modules expressions and statements and
// procedural macros to expand to non-items.
val PROC_MACRO_MOD = CompilerFeature("proc_macro_mod", ACTIVE, "1.27.0")
val PROC_MACRO_EXPR = CompilerFeature("proc_macro_expr", ACTIVE, "1.27.0")
val PROC_MACRO_NON_ITEMS = CompilerFeature("proc_macro_non_items", ACTIVE, "1.27.0")
val PROC_MACRO_GEN = CompilerFeature("proc_macro_gen", ACTIVE, "1.27.0")
// #[doc(alias = "...")]
val DOC_ALIAS = CompilerFeature("doc_alias", ACTIVE, "1.27.0")
// Access to crate names passed via `--extern` through prelude
val EXTERN_PRELUDE = CompilerFeature("extern_prelude", ACTIVE, "1.27.0")
// Scoped attributes
val TOOL_ATTRIBUTES = CompilerFeature("tool_attributes", ACTIVE, "1.25.0")
// Scoped lints
val TOOL_LINTS = CompilerFeature("tool_lints", ACTIVE, "1.28.0")
// allow irrefutable patterns in if-let and while-let statements (RFC 2086)
val IRREFUTABLE_LET_PATTERNS = CompilerFeature("irrefutable_let_patterns", ACTIVE, "1.27.0")
// Allows use of the :literal macro fragment specifier (RFC 1576)
val MACRO_LITERAL_MATCHER = CompilerFeature("macro_literal_matcher", ACTIVE, "1.27.0")
// inconsistent bounds in where clauses
val TRIVIAL_BOUNDS = CompilerFeature("trivial_bounds", ACTIVE, "1.28.0")
// 'a: { break 'a; }
val LABEL_BREAK_VALUE = CompilerFeature("label_break_value", ACTIVE, "1.28.0")
// #[panic_implementation]
val PANIC_IMPLEMENTATION = CompilerFeature("panic_implementation", ACTIVE, "1.28.0")
// #[doc(keyword = "...")]
val DOC_KEYWORD = CompilerFeature("doc_keyword", ACTIVE, "1.28.0")
// Allows async and await syntax
val ASYNC_AWAIT = CompilerFeature("async_await", ACTIVE, "1.28.0")
// #[alloc_error_handler]
val ALLOC_ERROR_HANDLER = CompilerFeature("alloc_error_handler", ACTIVE, "1.29.0")
val ABI_AMDGPU_KERNEL = CompilerFeature("abi_amdgpu_kernel", ACTIVE, "1.29.0")


val ASSOCIATED_TYPES = CompilerFeature("associated_types", ACCEPTED, "1.0.0")
// allow overloading augmented assignment operations like `a += b`
val AUGMENTED_ASSIGNMENTS = CompilerFeature("augmented_assignments", ACCEPTED, "1.8.0")
// allow empty structs and enum variants with braces
val BRACED_EMPTY_STRUCTS = CompilerFeature("braced_empty_structs", ACCEPTED, "1.8.0")
// Allows indexing into constant arrays.
val CONST_INDEXING = CompilerFeature("const_indexing", ACCEPTED, "1.26.0")
val DEFAULT_TYPE_PARAMS = CompilerFeature("default_type_params", ACCEPTED, "1.0.0")
val GLOBS = CompilerFeature("globs", ACCEPTED, "1.0.0")
val IF_LET = CompilerFeature("if_let", ACCEPTED, "1.0.0")
// A temporary feature gate used to enable parser extensions needed
// to bootstrap fix for #5723.
val ISSUE_5723_BOOTSTRAP = CompilerFeature("issue_5723_bootstrap", ACCEPTED, "1.0.0")
val MACRO_RULES = CompilerFeature("macro_rules", ACCEPTED, "1.0.0")
// Allows using #![no_std]
val NO_STD = CompilerFeature("no_std", ACCEPTED, "1.6.0")
val SLICING_SYNTAX = CompilerFeature("slicing_syntax", ACCEPTED, "1.0.0")
val STRUCT_VARIANT = CompilerFeature("struct_variant", ACCEPTED, "1.0.0")
// These are used to test this portion of the compiler, they don't actually
// mean anything
val TEST_ACCEPTED_FEATURE = CompilerFeature("test_accepted_feature", ACCEPTED, "1.0.0")
val TUPLE_INDEXING = CompilerFeature("tuple_indexing", ACCEPTED, "1.0.0")
// Allows macros to appear in the type position.
val TYPE_MACROS = CompilerFeature("type_macros", ACCEPTED, "1.13.0")
val WHILE_LET = CompilerFeature("while_let", ACCEPTED, "1.0.0")
// Allows `#[deprecated]` attribute
val DEPRECATED = CompilerFeature("deprecated", ACCEPTED, "1.9.0")
// `expr?`
val QUESTION_MARK = CompilerFeature("question_mark", ACCEPTED, "1.13.0")
// Allows `..` in tuple (struct) patterns
val DOTDOT_IN_TUPLE_PATTERNS = CompilerFeature("dotdot_in_tuple_patterns", ACCEPTED, "1.14.0")
val ITEM_LIKE_IMPORTS = CompilerFeature("item_like_imports", ACCEPTED, "1.15.0")
// Allows using `Self` and associated types in struct expressions and patterns.
val MORE_STRUCT_ALIASES = CompilerFeature("more_struct_aliases", ACCEPTED, "1.16.0")
// elide `'static` lifetimes in `static`s and `const`s
val STATIC_IN_CONST = CompilerFeature("static_in_const", ACCEPTED, "1.17.0")
// Allows field shorthands (`x` meaning `x: x`) in struct literal expressions.
val FIELD_INIT_SHORTHAND = CompilerFeature("field_init_shorthand", ACCEPTED, "1.17.0")
// Allows the definition recursive static items.
val STATIC_RECURSION = CompilerFeature("static_recursion", ACCEPTED, "1.17.0")
// pub(restricted) visibilities (RFC 1422)
val PUB_RESTRICTED = CompilerFeature("pub_restricted", ACCEPTED, "1.18.0")
// The #![windows_subsystem] attribute
val WINDOWS_SUBSYSTEM = CompilerFeature("windows_subsystem", ACCEPTED, "1.18.0")
// Allows `break {expr}` with a value inside `loop`s.
val LOOP_BREAK_VALUE = CompilerFeature("loop_break_value", ACCEPTED, "1.19.0")
// Permits numeric fields in struct expressions and patterns.
val RELAXED_ADTS = CompilerFeature("relaxed_adts", ACCEPTED, "1.19.0")
// Coerces non capturing closures to function pointers
val CLOSURE_TO_FN_COERCION = CompilerFeature("closure_to_fn_coercion", ACCEPTED, "1.19.0")
// Allows attributes on struct literal fields.
val STRUCT_FIELD_ATTRIBUTES = CompilerFeature("struct_field_attributes", ACCEPTED, "1.20.0")
// Allows the definition of associated constants in `trait` or `impl`
// blocks.
val ASSOCIATED_CONSTS = CompilerFeature("associated_consts", ACCEPTED, "1.20.0")
// Usage of the `compile_error!` macro
val COMPILE_ERROR = CompilerFeature("compile_error", ACCEPTED, "1.20.0")
// See rust-lang/rfcs#1414. Allows code like `let x: &'static u32 = &42` to work.
val RVALUE_STATIC_PROMOTION = CompilerFeature("rvalue_static_promotion", ACCEPTED, "1.21.0")
// Allow Drop types in constants (RFC 1440)
val DROP_TYPES_IN_CONST = CompilerFeature("drop_types_in_const", ACCEPTED, "1.22.0")
// Allows the sysV64 ABI to be specified on all platforms
// instead of just the platforms on which it is the C ABI
val ABI_SYSV64 = CompilerFeature("abi_sysv64", ACCEPTED, "1.24.0")
// Allows `repr(align(16))` struct attribute (RFC 1358)
val REPR_ALIGN = CompilerFeature("repr_align", ACCEPTED, "1.25.0")
// allow '|' at beginning of match arms (RFC 1925)
val MATCH_BEGINNING_VERT = CompilerFeature("match_beginning_vert", ACCEPTED, "1.25.0")
// Nested groups in `use` (RFC 2128)
val USE_NESTED_GROUPS = CompilerFeature("use_nested_groups", ACCEPTED, "1.25.0")
// a..=b and ..=b
val INCLUSIVE_RANGE_SYNTAX = CompilerFeature("inclusive_range_syntax", ACCEPTED, "1.26.0")
// allow `..=` in patterns (RFC 1192)
val DOTDOTEQ_IN_PATTERNS = CompilerFeature("dotdoteq_in_patterns", ACCEPTED, "1.26.0")
// Termination trait in main (RFC 1937)
val TERMINATION_TRAIT = CompilerFeature("termination_trait", ACCEPTED, "1.26.0")
// Copy/Clone closures (RFC 2132)
val CLONE_CLOSURES = CompilerFeature("clone_closures", ACCEPTED, "1.26.0")
val COPY_CLOSURES = CompilerFeature("copy_closures", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function arguments.
val UNIVERSAL_IMPL_TRAIT = CompilerFeature("universal_impl_trait", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function return types.
val CONSERVATIVE_IMPL_TRAIT = CompilerFeature("conservative_impl_trait", ACCEPTED, "1.26.0")
// The `i128` type
val I128_TYPE = CompilerFeature("i128_type", ACCEPTED, "1.26.0")
// Default match binding modes (RFC 2005)
val MATCH_DEFAULT_BINDINGS = CompilerFeature("match_default_bindings", ACCEPTED, "1.26.0")
// allow `'_` placeholder lifetimes
val UNDERSCORE_LIFETIMES = CompilerFeature("underscore_lifetimes", ACCEPTED, "1.26.0")
// Allows attributes on lifetime/type formal parameters in generics (RFC 1327)
val GENERIC_PARAM_ATTRS = CompilerFeature("generic_param_attrs", ACCEPTED, "1.27.0")
// Allows cfg(target_feature = "...").
val CFG_TARGET_FEATURE = CompilerFeature("cfg_target_feature", ACCEPTED, "1.27.0")
// Allows #[target_feature(...)]
val TARGET_FEATURE = CompilerFeature("target_feature", ACCEPTED, "1.27.0")
// Trait object syntax with `dyn` prefix
val DYN_TRAIT = CompilerFeature("dyn_trait", ACCEPTED, "1.27.0")
// allow `#[must_use]` on functions; and, must-use operators (RFC 1940)
val FN_MUST_USE = CompilerFeature("fn_must_use", ACCEPTED, "1.27.0")
// Allows use of the :lifetime macro fragment specifier
val MACRO_LIFETIME_MATCHER = CompilerFeature("macro_lifetime_matcher", ACCEPTED, "1.27.0")
// Termination trait in tests (RFC 1937)
val TERMINATION_TRAIT_TEST = CompilerFeature("termination_trait_test", ACCEPTED, "1.27.0")
// The #[global_allocator] attribute
val GLOBAL_ALLOCATOR = CompilerFeature("global_allocator", ACCEPTED, "1.28.0")
// Allows `#[repr(transparent)]` attribute on newtype structs
val REPR_TRANSPARENT = CompilerFeature("repr_transparent", ACCEPTED, "1.28.0")

data class CompilerFeature(val name: String, val state: FeatureState, val since: SemVer) {
    constructor(name: String, state: FeatureState, since: String) : this(name, state, SemVer.parseFromText(since)!!)
}

enum class FeatureState {
    /**
     * Represents active features that are currently being implemented or
     * currently being considered for addition/removal.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    ACTIVE,
    /**
     * Those language feature has since been Accepted (it was once Active)
     * so such language features can be used with stable/beta compiler since some version
     * without any additional attributes
     */
    ACCEPTED
}
