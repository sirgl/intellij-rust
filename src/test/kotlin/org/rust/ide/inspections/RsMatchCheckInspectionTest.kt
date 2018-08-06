package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun testSimple() = checkByText("""
        fn main() {
            let a = 5;
            match a {
                1 ... 3 => {}
                _ => {}
            };
        }
    """)
}
