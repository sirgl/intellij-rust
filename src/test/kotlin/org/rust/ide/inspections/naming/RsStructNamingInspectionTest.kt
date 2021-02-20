/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsStructNamingInspection

class RsStructNamingInspectionTest : RsInspectionsTestBase(RsStructNamingInspection()) {
    fun `test structs`() = checkByText("""
        struct StructOk {}
        struct <warning descr="Type `struct_foo` should have a camel case name such as `StructFoo`">struct_foo</warning> {}
    """)

    fun `test structs suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        struct struct_foo {}
    """)

    fun `test structs fix`() = checkFixByText("Rename to `StructFoo`", """
        struct <warning descr="Type `Struct_foo` should have a camel case name such as `StructFoo`">Stru<caret>ct_foo</warning> {}
        fn struct_use() {
            let a = Struct_foo {};
        }
    """, """
        struct StructFoo {}
        fn struct_use() {
            let a = StructFoo {};
        }
    """)
}
