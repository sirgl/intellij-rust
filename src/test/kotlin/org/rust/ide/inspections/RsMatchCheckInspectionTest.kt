package org.rust.ide.inspections

import org.rust.ide.inspections.checkMatch.RsMatchCheckInspection

class RsMatchCheckInspectionTest : RsInspectionsTestBase(RsMatchCheckInspection()) {

    // Simple type useless tests (boolean, int, double, string, char, path(enum, struct without field) )
    fun testSimpleBooleanUseless() = checkByText("""
        fn main() {
            let a = true;
            match a {
                true => {}
                false => {}
                <error descr="Useless match arm">true => {}</error>
                <error descr="Useless match arm">true => {}</error>
                <error descr="Useless match arm">false => {}</error>
            }
        }
    """)

    fun testSimpleIntUseless() = checkByText("""
        fn main() {
            let a = 2;
            match a {
                1 => {}
                2 => {}
                <error descr="Useless match arm">1 => {}</error>
                _ => {}
            }
        }
    """)

    fun testSimpleDoubleUseless() = checkByText("""
        fn main() {
            let a = 9.9;
            match a {
                1.0 => {}
                2.3 => {}
                3.6 => {}
                <error descr="Useless match arm">2.3 => {}</error>
                _ => {}
            }
        }
    """)

    fun testSimpleStringUseless() = checkByText("""
        fn main() {
            let a = "str";
            match a {
                "test" => {}
                "test2" => {}
                <error descr="Useless match arm">"test" => {}</error>
                _ => {}
            }
        }
    """)

    fun testSimpleCharUseless() = checkByText("""
        fn main() {
            let a = 'h';
            match a {
                'c' => {}
                'd' => {}
                <error descr="Useless match arm">'c' => {}</error>
                _ => {}
            }
        }
    """)

    fun testSimplePathEnumUseless() = checkByText("""
        enum TEST {
            A, B
        }
        fn main() {
            let a = TEST::A;
            match a {
                TEST::A => {}
                TEST::B => {}
                <error descr="Useless match arm">TEST::A => {}</error>
                <error descr="Useless match arm">TEST::B => {}</error>
            }
        }
    """)

    fun testSimplePathStructWFUseless() = checkByText("""
        struct TEST;
        fn main() {
            let a = TEST;
            match a {
                TEST => {}
                <error descr="Useless match arm">TEST => {}</error>
            }
        }
    """)

    // Enum with fields
    fun testEnumWithBooleanUseless() = checkByText("""
        enum TEST {
            A(bool)
        }
        fn main() {
            let a = TEST::A(true);
            match a {
                TEST::A(true) => {}
                TEST::A(false) => {}
                <error descr="Useless match arm">TEST::A(true) => {}</error>
            }
        }
    """)

    fun testEnumWithIntUseless() = checkByText("""
        enum TEST {
            A(i32)
        }
        fn main() {
            let a = TEST::A(2);
            match a {
                TEST::A(3) => {}
                TEST::A(5) => {}
                <error descr="Useless match arm">TEST::A(3) => {}</error>
                TEST::A(x) => {}
            }
        }
    """)

    fun testEnumWithDoubleUseless() = checkByText("""
        enum TEST {
            A(f64)
        }
        fn main() {
            let a = TEST::A(2.3);
            match a {
                TEST::A(3.6) => {}
                TEST::A(5.8) => {}
                <error descr="Useless match arm">TEST::A(3.6) => {}</error>
                TEST::A(x) => {}
            }
        }
    """)

    fun testEnumWithStringUseless() = checkByText("""
        enum TEST {
            A(str)
        }
        fn main() {
            let a = TEST::A("str");
            match a {
                TEST::A("test") => {}
                TEST::A("test2") => {}
                <error descr="Useless match arm">TEST::A("test") => {}</error>
                TEST::A(x) => {}
            }
        }
    """)

    fun testEnumWithCharUseless() = checkByText("""
        enum TEST {
            A(char)
        }
        fn main() {
            let a = TEST::A('c');
            match a {
                TEST::A('d') => {}
                TEST::A('b') => {}
                <error descr="Useless match arm">TEST::A('b') => {}</error>
                TEST::A(x) => {}
            }
        }
    """)

    fun testEnumWithPathEnumUseless() = checkByText("""
        enum ONE {
            A, B
        }
        enum TEST {
            A(ONE)
        }
        fn main() {
            let a = TEST::A(ONE::A);
            match a {
                TEST::A(ONE::A) => {}
                TEST::A(ONE::B) => {}
                <error descr="Useless match arm">TEST::A(ONE::A) => {}</error>
            }
        }
    """)

    fun testEnumWithPathStructWFUseless() = checkByText("""
        struct ONE;
        enum TEST {
            A(ONE)
        }
        fn main() {
            let a = TEST::A(ONE);
            match a {
                TEST::A(ONE) => {}
                <error descr="Useless match arm">TEST::A(ONE) => {}</error>
            }
        }
    """)

    fun testEnumWithEnumUseless() = checkByText("""

        enum ONE {
            A(i32),
            B
        }
        enum TWO {
            A(str),
            B(ONE)
        }
        enum TEST {
            A(TWO)
        }
        fn main() {
            let a = TEST::A(TWO::B(ONE::A(2)));
            match a {
                TEST::A(TWO::B(ONE::A(3))) => {}
                TEST::A(TWO::B(ONE::A(x))) => {}
                TEST::A(TWO::B(ONE::B)) => {}
                TEST::A(TWO::A("str")) => {}
                <error descr="Useless match arm">TEST::A(TWO::B(ONE::A(5))) => {}</error>
                <error descr="Useless match arm">TEST::A(TWO::B(ONE::A(_))) => {}</error>
                <error descr="Useless match arm">TEST::A(TWO::B(ONE::B)) => {}</error>
                <error descr="Useless match arm">TEST::A(TWO::B(_)) => {}</error>
                <error descr="Useless match arm">TEST::A(TWO::A("str")) => {}</error>
                TEST::A(TWO::A(_)) => {}
                <error descr="Useless match arm">TEST::A(TWO::A("str2")) => {}</error>
            }
        }
    """)

    // Struct with fields
    fun testStructWithBooleanUseless() = checkByText("""
        struct TEST {
            a: bool
        }
        fn main() {
            let a = TEST { a: true };
            match a {
                TEST { a: true } => {}
                TEST { a: false } => {}
                <error descr="Useless match arm">TEST { a: true } => {}</error>
                <error descr="Useless match arm">TEST { a: x } => {}</error>
                <error descr="Useless match arm">TEST { a } => {}</error>
            }
        }
    """)

    fun testStructWithIntUseless() = checkByText("""
        struct TEST {
            a: i32
        }
        fn main() {
            let a = TEST { a: 1 };
            match a {
                TEST { a: 3 } => {}
                TEST { a: 4 } => {}
                <error descr="Useless match arm">TEST { a: 3 } => {}</error>
                TEST { a } => {}
                <error descr="Useless match arm">TEST { a: x } => {}</error>
            }
        }
    """)

    fun testStructWithDoubleUseless() = checkByText("""
        struct TEST {
            a: f64
        }
        fn main() {
            let a = TEST { a: 1.6 };
            match a {
                TEST { a: 3.4 } => {}
                TEST { a: 4.1 } => {}
                <error descr="Useless match arm">TEST { a: 3.4 } => {}</error>
                TEST { a } => {}
                <error descr="Useless match arm">TEST { a: x } => {}</error>
            }
        }
    """)

    fun testStructWithStringUseless() = checkByText("""
        struct TEST {
            a: str
        }
        fn main() {
            let a = TEST { a: "str" };
            match a {
                TEST { a: "test" } => {}
                TEST { a: "test2" } => {}
                <error descr="Useless match arm">TEST { a: "test" } => {}</error>
                TEST { a } => {}
                <error descr="Useless match arm">TEST { a: x } => {}</error>
            }
        }
    """)

    fun testStructWithCharUseless() = checkByText("""
        struct TEST {
            a: char
        }
        fn main() {
            let a = TEST { a: 'c' };
            match a {
                TEST { a: 'w' } => {}
                TEST { a: 'c' } => {}
                <error descr="Useless match arm">TEST { a: 'w' } => {}</error>
                TEST { a } => {}
                <error descr="Useless match arm">TEST { a: x } => {}</error>
            }
        }
    """)

    fun testStructWithPathEnumUseless() = checkByText("""
        enum ONE {
            A, B
        }
        struct TEST {
            a: ONE
        }
        fn main() {
            let a = TEST { a: ONE::A };
            match a {
                TEST { a: ONE::A } => {}
                TEST { a: ONE::B } => {}
                <error descr="Useless match arm">TEST { a: ONE::A } => {}</error>
                <error descr="Useless match arm">TEST { a } => {}</error>
                <error descr="Useless match arm">TEST { a: x } => {}</error>

            }
        }
    """)

    fun testStructWithPathStructWFUseless() = checkByText("""
        struct ONE;
        struct TEST {
            a: ONE
        }
        fn main() {
            let a = TEST { a: ONE };
            match a {
                TEST { a: ONE } => {}
                <error descr="Useless match arm">TEST { a: ONE } => {}</error>
                <error descr="Useless match arm">TEST { a } => {}</error>
                <error descr="Useless match arm">TEST { a: x } => {}</error>

            }
        }
    """)

    fun testStructWithStructUseless() = checkByText("""
        struct ONE {
            a: i32,
            b: char
        }
        struct TEST {
            a: ONE
        }
        fn main() {
            let a = TEST { a: ONE {a: 2, b: 'c'} };
            match a {
                TEST { a: ONE { a: 2, b: 'w' } } => {}
                <error descr="Useless match arm">TEST { a: ONE { b: 'w', a: 2 } } => {}</error>
                TEST { a: ONE { a: _, b: 'w' } } => {}
                <error descr="Useless match arm">TEST { a: ONE { b: 'w', a: 999 } } => {}</error>
                TEST { a: ONE { a: _, b: _ } } => {}
                <error descr="Useless match arm">_ => {}</error>
            }
        }
    """)

    // =================================================================================================================

    fun testSimpleBooleanExhaustive() = checkFixByText("Add false pattern","""
        fn main() {
            let a = true;
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                true => {}
            }
        }
    """, """
        fn main() {
            let a = true;
            match a {
                true => {}
                false => {}
            }
        }
    """)

    fun testSimpleIntExhaustive() = checkFixByText("Add _ pattern","""
        fn main() {
            let a = 3;
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                3 => {}
                1 => {}
            }
        }
    """, """
        fn main() {
            let a = 3;
            match a {
                3 => {}
                1 => {}
                _ => {}
            }
        }
    """)

    fun testSimpleDoubleExhaustive() = checkFixByText("Add _ pattern","""
        fn main() {
            let a = 3.9;
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                3.1 => {}
                1.777 => {}
            }
        }
    """, """
        fn main() {
            let a = 3.9;
            match a {
                3.1 => {}
                1.777 => {}
                _ => {}
            }
        }
    """)

    fun testSimpleStringExhaustive() = checkFixByText("Add &_ pattern","""
        fn main() {
            let a = "str";
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                "test1" => {}
                "test2" => {}
            }
        }
    """, """
        fn main() {
            let a = "str";
            match a {
                "test1" => {}
                "test2" => {}
                &_ => {}
            }
        }
    """)

    fun testSimpleCharExhaustive() = checkFixByText("Add _ pattern","""
        fn main() {
            let a = 'c';
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                'w' => {}
                'h' => {}
            }
        }
    """, """
        fn main() {
            let a = 'c';
            match a {
                'w' => {}
                'h' => {}
                _ => {}
            }
        }
    """)

    fun testSimplePathExhaustive() = checkFixByText("Add ONE::A pattern","""
        enum ONE {
            A, B
        }
        fn main() {
            let a = ONE::A;
            <error descr="Match must be exhaustive">mat<caret>ch</error> a {
                ONE::B => {}
            }
        }
    """, """
        enum ONE {
            A, B
        }
        fn main() {
            let a = ONE::A;
            match a {
                ONE::B => {}
                ONE::A => {}
            }
        }
    """)
}
