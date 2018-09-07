package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun uselessWildcard() = checkByText("""
        enum TEST {
            A,
            B
        }
        fn main() {
            let a = TEST::A;
            match a {
                TEST::A => {}
                TEST::B => {}
                <error descr="Useless match arm">_ => {}</error>
            }
        }
    """)

    fun uselessArmAfterExhaustive() = checkByText("""
        enum TEST {
            A(i32),
            B
        }

        fn main() {
            let a = TEST::A(2);
            match a {
                TEST::A(2) => {}
                TEST::B => {}
                TEST::A(_) => {}
                <error descr="Useless match arm">TEST::A(3) => {}</error>
            }
        }
    """)

    fun uselessTwiceArm() = checkByText("""
        enum TEST {
            A(i32),
            B
        }

        fn main() {
            let a = TEST::A(3);
            match a {
                TEST::A(2) => {}
                <error descr="Useless match arm">TEST::A(2) => {}</error>
                TEST::B => {}
            }
        }
    """)

    fun uselessArmsAtherWildcard() = checkByText("""
        enum TEST {
            A(i32),
            B
        }

        fn main() {
            let a = TEST::A(2);
            match a {
                TEST::A(32) => {}
                TEST::B => {}
                _ => {}
                <error descr="Useless match arm">TEST::A(23) => {}</error>
                <error descr="Useless match arm">TEST::B => {}</error>
                <error descr="Useless match arm">TEST::A(_) => {}</error>
            }
        }
    """)

    fun uselessWildcardNested() = checkByText("""
        enum ONE {
            A,
            B(TWO)
        }
        enum TWO {
            A(i32),
            B
        }
        fn main() {
            let a = ONE::B(TWO::A(2));
            match a {
                ONE::A => {}
                ONE::B(_) => {}
                <error descr="Useless match arm">_ => {}</error>
            }
        }
    """)

    fun uselessNested() = checkByText("""
        enum ONE {
            A,
            B(TWO)
        }
        enum TWO {
            A(i32),
            B
        }
        fn main() {
            let a = ONE::B(TWO::A(2));
            match a {
                ONE::A => {}
                ONE::B(TWO::A(_)) => {}
                ONE::B(TWO::B) => {}
                <error descr="Useless match arm">_ => {}</error>
            }
        }
    """)



}
