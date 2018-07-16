package org.rust.ide.annotator

class RsControlFlowAnnotatorTest : RsAnnotatorTestBase() {
    fun `test no return simple`() = checkErrors("""
        fn foo() -> i32 {

        <error descr="Missing return">}</error>
    """)

    fun `test no return unit fun`() = checkErrors("""
        fn foo() {

        }
    """)

    fun `test no return if`() = checkErrors("""
        fn foo() -> i32 {
            if () {
                return 12
            } else {
            };
        }
    """)
}
