/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import org.rust.RsTestBase

class RsRainbowVisitorTest : RsTestBase() {

    fun `test path binding`() = checkRainbow("""
        fn main() {
            let mut <rainbow>test</rainbow> = "";
            <rainbow>test</rainbow> = "";
        }
    """)

    fun `test function binding`() = checkRainbow("""
        fn foo() {}
        fn main() {
            foo();
        }
    """, true)

    fun `test different color`() = checkRainbow("""
        fn main() {
            let mut <rainbow color='ff000002'>test</rainbow> = "";
            <rainbow color='ff000002'>test</rainbow> = "";
            let mut <rainbow color='ff000003'>test</rainbow> = "";
            <rainbow color='ff000003'>test</rainbow> = "";
        }
    """, withColor = true)

    fun `test complex different color`() = checkRainbow("""
        fn foo(<rainbow color='ff000002'>test</rainbow>: i32) {
            let <rainbow color='ff000004'>x</rainbow> = <rainbow color='ff000002'>test</rainbow> + <rainbow color='ff000002'>test</rainbow>;
            let <rainbow color='ff000001'>y</rainbow> = {
               let <rainbow color='ff000003'>test</rainbow> = <rainbow color='ff000004'>x</rainbow>;
            };
            <rainbow color='ff000002'>test</rainbow>
        }
    """, withColor = true)

    private fun checkRainbow(code: String, isRainbowOn: Boolean = true, withColor: Boolean = false) {
        myFixture.testRainbow("main.rs", code, isRainbowOn, withColor)
    }
}
