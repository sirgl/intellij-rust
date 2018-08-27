package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun testSimple() = checkByText("""
        enum TWO {
            A,
            B(THREE),
        }

        enum THREE {
            END
        }

        enum ONE {
            A{
                a: i32,
                b: i32
            },
            B(TWO, i32)
        }

        struct TEST {
            a: i32
        }

        fn main() {
            let a = ONE::A{
                a: 4,
                b: 2
            };
            let b = 2;
//            match b {
//                1 | 2 => {},
//                2 => {},
//                1 .. 2 => {},
//                4 ... 5 => {},
//                _ => {},
//                x => {},
//                () => {}
//            }
            match a {
                ONE::A{a: _, b: x} => {},
                ONE::B(TWO::A, _) => {},
            }
        }


    """)
}
