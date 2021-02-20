/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

class RsMacroExpansionResolveTest : RsResolveTestBase() {
    fun `test lazy static`() = checkByCode("""
        #[macro_use]
        extern crate lazy_static;

        struct Foo {}
        impl Foo {
            fn new() -> Foo { Foo {} }
            fn bar(&self) {}
        }     //X

        lazy_static! { static ref FOO: Foo = Foo::new(); }

        fn main() {
            FOO.bar()
        }      //^
    """)

    fun `test multiple lazy static 1`() = checkByCode("""
        #[macro_use]
        extern crate lazy_static;

        struct Foo {}
        impl Foo {
            fn new() -> Foo { Foo {} }
            fn bar (&self) {}
        }     //X

        lazy_static! {
            static ref FOO_1: Foo = Foo::new();
            static ref FOO_2: Foo = Foo::new();
        }

        fn main() {
            FOO_1.bar();
                 //^
        }
    """)

    fun `test multiple lazy static 2`() = checkByCode("""
        #[macro_use]
        extern crate lazy_static;

        struct Foo {}
        impl Foo {
            fn new() -> Foo { Foo {} }
            fn bar (&self) {}
        }     //X

        lazy_static! {
            static ref FOO_1: Foo = Foo::new();
            static ref FOO_2: Foo = Foo::new();
        }

        fn main() {
            FOO_2.bar();
                 //^
        }
    """)

    fun `test expand item`() = checkByCode("""
        macro_rules! if_std {
            ($ i:item) => (
                #[cfg(feature = "use_std")]
                $ i
            )
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test expand items star`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                #[cfg(feature = "use_std")]
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test expand items star with reexport`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                #[cfg(feature = "use_std")]
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            use Foo as Bar;
        }

        fn main() {
            Bar.bar()
        }      //^
    """)

    fun `test expand items star with reexport from expansion`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                #[cfg(feature = "use_std")]
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
            use foo as bar;
        }

        fn main() {
            bar().bar()
        }        //^
    """)

    fun `test expand items star with nested macro calls`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                #[cfg(feature = "use_std")]
                $ i
            )*)
        }

        macro_rules! foo {
            ($ ($ i:item)*) => ($ (
                if_std! { $ i }
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        foo! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test expand items star with infinite recursive nested macro calls`() = checkByCode("""
        macro_rules! foo {
            ($ ($ i:item)*) => ($ (
                foo! { $ i }
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }

        foo! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^ unresolved
    """)

    fun `test method defined with a macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        struct Foo;
        struct Bar;
        impl Foo { foo!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test trait method defined with a macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        trait FooTrait { foo!(foo, Bar); }
        struct Foo;
        struct Bar;
        impl FooTrait for Foo {}
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test method defined with a nested macro call`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        macro_rules! bar {
            ($ i:ident, $ j:ty) => { foo!($ i, $ j); }
        }

        struct Foo;
        struct Bar;
        impl Foo { bar!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test expand impl members with infinite recursive nested macro calls`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { foo!($ i, $ j) }
        }

        struct Foo;
        struct Bar;
        impl Foo { bar!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
        fn main() {
            Foo.foo().bar();
        }           //^ unresolved
    """)

    fun `test 'crate' metavar in same crate`() = checkByCode("""
        struct Foo;
        impl Foo {
            pub fn bar(&self) {}
        }     //X

        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
        foo!();

        fn main() {
            foo().bar()
        }       //^
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package;

        foo!();

        fn main() {
            foo().bar()
        }       //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with alias`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        foo!();

        fn main() {
            foo().bar()
        }       //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with macro call not in crate root`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        mod a {
            foo!();

            fn main() {
                foo().bar()
            }       //^ lib.rs
        }
    """)
}
