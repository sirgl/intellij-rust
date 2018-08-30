package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun testSimple() = checkByText("""
        enum TWO {
            A,
            B(THREE),
        }

        enum THREE {
            END,
            START
        }

        enum ONE {
            A{
                a: i32,
                b: i32
            },
            B(TWO, i32, TWO)
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
            let c = THREE::END;
            match b {
                1 => {}
                2 => {}
                3 => {}
                2 => {}
                _ => {}
            }
//            match a {
//                ONE::A{b: x, a: 2} => {},
//                ONE::A{a: x, b: 2} => {},
//                ONE::B(TWO::A, _, x) => {},
//            }
        }


    """)
}
