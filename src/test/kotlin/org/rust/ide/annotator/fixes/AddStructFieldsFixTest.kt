/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotationTestBase

class AddStructFieldsFixTest : RsAnnotationTestBase() {
    fun `test no fields`() = checkBothQuickFix("""
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = <error>S</error> { /*caret*/ };
        }
    """, """
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = S { foo: /*caret*/0, bar: 0.0 };
        }
    """)

    fun `test aliased struct`() = checkBothQuickFix("""
        struct S { foo: i32, bar: f64 }
        type T = S;

        fn main() {
            let _ = <error>T</error> { /*caret*/ };
        }
    """, """
        struct S { foo: i32, bar: f64 }
        type T = S;

        fn main() {
            let _ = T { foo: /*caret*/0, bar: 0.0 };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no comma`() = checkBothQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            <error>S</error> { a: 92/*caret*/};
        }
    """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: /*caret*/String::new() };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test with comma`() = checkBothQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            <error>S</error> { a: 92, /*caret*/};
        }
    """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: /*caret*/String::new() };
        }
    """)

    fun `test some existing fields`() = checkBothQuickFix("""
        struct S { a: i32, b: i32, c: i32, d: i32 }

        fn main() {
            let _ = <error>S</error> {
                a: 92,
                c: 92/*caret*/
            };
        }
    """, """
        struct S { a: i32, b: i32, c: i32, d: i32 }

        fn main() {
            let _ = S {
                a: 92,
                b: /*caret*/0,
                c: 92,
                d: 0
            };
        }
    """)

    fun `test first field is added first`() = checkBothQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = <error>S</error> { b: 0,/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: /*caret*/0, b: 0, };
        }
    """)

    fun `test last field is added last`() = checkBothQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = <error>S</error> { /*caret*/a: 0 };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: 0, b: /*caret*/0 };
        }
    """)

    fun `test preserves order`() = checkBothQuickFix("""
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = <error>S</error> { a: 0, c: 1, e: 2/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = S { a: 0, b: /*caret*/0, c: 1, d: 0, e: 2 };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test issue 980`() = checkBothQuickFix("""
        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
        }

        fn main() {
            Mesh{
                name: String::new(),
                vertices: Vec::new(),
                faces: Vec::new(),
                material: None
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test many type fields`() = checkBothQuickFix("""
        type AliasedString = String;

        struct DataContainer<'a > {
            bool_field: bool,
            char_field: char,
            i8_field: i8,
            i16_field: i16,
            i32_field: i32,
            i64_field: i64,
            u8_field: u8,
            u16_field: u16,
            u32_field: u32,
            u64_field: u64,
            isize_field: isize,
            usize_field: usize,
            f32_field: f32,
            f64_field: f64,
            slice_field: [i32],
            array_field: [i32; 3],
            str_field: String,
            vec_field: Vec<i32>,
            opt_field: Option<i32>,
            ref_field: &'a String,
            ref_mut_field: &'a mut String,
            tuple_field: (bool, char, i8, String),
            aliased_field: AliasedString,
            unsupported_type_field: fn(i32) -> i32
        }

        fn main() {
            <error>DataContainer</error>{/*caret*/};
        }
    """, """
        type AliasedString = String;

        struct DataContainer<'a > {
            bool_field: bool,
            char_field: char,
            i8_field: i8,
            i16_field: i16,
            i32_field: i32,
            i64_field: i64,
            u8_field: u8,
            u16_field: u16,
            u32_field: u32,
            u64_field: u64,
            isize_field: isize,
            usize_field: usize,
            f32_field: f32,
            f64_field: f64,
            slice_field: [i32],
            array_field: [i32; 3],
            str_field: String,
            vec_field: Vec<i32>,
            opt_field: Option<i32>,
            ref_field: &'a String,
            ref_mut_field: &'a mut String,
            tuple_field: (bool, char, i8, String),
            aliased_field: AliasedString,
            unsupported_type_field: fn(i32) -> i32
        }

        fn main() {
            DataContainer{
                bool_field: false,
                char_field: '',
                i8_field: 0,
                i16_field: 0,
                i32_field: 0,
                i64_field: 0,
                u8_field: 0,
                u16_field: 0,
                u32_field: 0,
                u64_field: 0,
                isize_field: 0,
                usize_field: 0,
                f32_field: 0.0,
                f64_field: 0.0,
                slice_field: [],
                array_field: [],
                str_field: String::new(),
                vec_field: Vec::new(),
                opt_field: None,
                ref_field: &String::new(),
                ref_mut_field: &mut String::new(),
                tuple_field: (false, '', 0, String::new()),
                aliased_field: String::new(),
                unsupported_type_field: ()
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test 1-level recursively fill struct`() = checkRecursiveQuickFix("""
        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            Mesh{
                name: String::new(),
                vertices: Vec::new(),
                faces: Vec::new(),
                material: None,
                metadata: MetaData {
                    author: String::new(),
                    licence: None,
                    specVersion: 0
                }
            };
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test 2-level recursively fill struct`() = checkRecursiveQuickFix("""
        struct ToolInfo {
            name: String,
            toolVersion: String,
        }

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32,
            tool: ToolInfo
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            <error>Mesh</error>{/*caret*/};
        }
    """, """
        struct ToolInfo {
            name: String,
            toolVersion: String,
        }

        struct MetaData {
            author: String,
            licence: Option<String>,
            specVersion: u32,
            tool: ToolInfo
        }

        struct Mesh {
            pub name: String,
            pub vertices: Vec<Vector3>,
            pub faces: Vec<Face>,
            pub material: Option<String>,
            pub metadata: MetaData
        }

        fn main() {
            Mesh{
                name: String::new(),
                vertices: Vec::new(),
                faces: Vec::new(),
                material: None,
                metadata: MetaData {
                    author: String::new(),
                    licence: None,
                    specVersion: 0,
                    tool: ToolInfo { name: String::new(), toolVersion: String::new() }
                }
            };
        }
    """)

    fun `test we don't filling struct that can't be instantiated (has private fields)`() = checkRecursiveQuickFix("""
        mod foo {
            pub struct Outer {
                pub inner: Inner,
                pub field2: i32
            }

            pub struct Inner {
                field1: i32,
                field2: i32
            }
        }

        fn main() {
            <error>foo::Outer</error> {/*caret*/};
        }
    """, """
        mod foo {
            pub struct Outer {
                pub inner: Inner,
                pub field2: i32
            }

            pub struct Inner {
                field1: i32,
                field2: i32
            }
        }

        fn main() {
            foo::Outer { inner: (), field2: 0 };
        }
    """)

    private fun checkBothQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) {
        checkFixByText("Add missing fields", before, after)
        checkFixByText("Recursively add missing fields", before, after)
    }

    private fun checkRecursiveQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkFixByText("Recursively add missing fields", before, after)

}
