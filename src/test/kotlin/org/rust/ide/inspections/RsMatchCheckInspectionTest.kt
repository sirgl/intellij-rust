package org.rust.ide.inspections

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {
    fun testUselessWildcard() = checkByText("""
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

    fun testUselessArmAfterExhaustive() = checkByText("""
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

    fun testUselessTwiceArm() = checkByText("""
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
                _ => {}
            }
        }
    """)

    fun testUselessArmsAtherWildcard() = checkByText("""
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

    fun testUselessWildcardNested() = checkByText("""
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

    fun testUselessNested() = checkByText("""
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


    fun testExhaustiveNumeric() = checkByText("""
        fn main() {
            let a = 4;
            <error descr="Match must be exhaustive">match</error> a {
                2 => {}
            }
        }
    """)

    fun testExhaustiveChar() = checkByText("""
        fn main() {
            let c = '2';
            <error descr="Match must be exhaustive">match</error> c {
                '3' => {}
                '4' => {}
            }
        }
    """)

    fun testExhaustiveEnum() = checkByText("""
        enum TEST {
            A,
            B
        }

        fn main() {
            let a = TEST::A;
            <error descr="Match must be exhaustive">match</error> a {
                TEST::B => {}
            }
        }
    """)

    fun testExhaustiveNestedEnum() = checkByText("""
        enum ONE {
            A(TWO),
            B
        }
        enum TWO {
            A,
            B(i32)
        }
        fn main() {
            let a = ONE::A(TWO::B(3));
            <error descr="Match must be exhaustive">match</error> a {
                ONE::A(TWO::A) => {}
                ONE::A(TWO::B(2)) => {}
                ONE::B => {}
            }
        }
    """)

}
