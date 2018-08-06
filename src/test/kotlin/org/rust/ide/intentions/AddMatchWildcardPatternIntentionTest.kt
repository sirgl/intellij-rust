package org.rust.ide.intentions

class AddMatchWildcardPatternIntentionTest : RsIntentionTestBase(AddMatchWildcardPatternIntention()) {
    fun testSimple() = doAvailableTest("""
        fn main() {
            let a = 3;
            match/*caret*/ a {

            }
        }
    """, """
        fn main() {
            let a = 3;
            match a {
                _ => {}
            }
        }
    """)

    fun testExist() = doAvailableTest("""
        fn main() {
            let a = 3;
            match/*caret*/ a {
                _ => {}
            }
        }
    """, """
        fn main() {
            let a = 3;
            match a {
                _ => {}
            }
        }
    """)
}
