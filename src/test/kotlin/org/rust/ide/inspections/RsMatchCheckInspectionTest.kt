package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun testSimple() = checkByText("""
        enum ONE {
            A,
            B
        }

        enum TWO {
            A(i32, u32),
            B {
                a: i32
            }
        }

        fn main() {
            let a = TWO::A(2, 2);
            match a {
                TWO::A(_) => {},
                TWO::B => {},
            }
        }

    """)
}
