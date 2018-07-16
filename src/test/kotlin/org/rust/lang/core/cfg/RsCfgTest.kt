package org.rust.lang.core.cfg

import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction

class RsCfgTest : RsTestBase() {

    fun `test empty`() = checkCfg("fn main() {}", "0 EMPTY")

    fun `test if`() = checkCfg(
        """
fn main() {
    if true {
    } else {
    }
}
        """.trimIndent(),
        """
0 CONDGOTO 1
1 EMPTY
        """.trimIndent()
    )

    fun `test var decl without init`() = checkCfg(
        """
fn main() {
    let x;
}
        """.trimIndent(),
        """
0 DECL x
1 EMPTY
        """.trimIndent()
    )

    fun `test var assignment`() = checkCfg(
        """
fn main() {
    let mut x = 12;
    x = 23;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 WRITE x
3 EMPTY
        """.trimIndent()
    )


    fun `test loop simple`() = checkCfg(
        """
fn main() {
    loop {
    }
}
        """.trimIndent(),
        """
0 GOTO 0
1 EMPTY
        """.trimIndent()
    )

    fun `test loop break`() = checkCfg(
        """
fn main() {
    loop {
        break;
    }
}
        """.trimIndent(),
        """
0 GOTO 2
1 GOTO 0
2 EMPTY
        """.trimIndent()
    )

    fun `test path`() = checkCfg(
        """
fn main() {
    let x = 23;
    x;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 READ x
3 EMPTY
        """.trimIndent()
    )

    fun `test self assignment`() = checkCfg(
        """
fn main() {
    let x = 23;
    x = x;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 READ x
3 WRITE x
4 EMPTY
        """.trimIndent()
    )

    fun `test call`() = checkCfg(
        """
fn main() {
    let x = 12;
    let y = 12;
    let z = foo(x, y);
}
fn foo(a: i32, b: i32){}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 DECL y
3 WRITE y
4 DECL z
5 READ x
6 READ y
7 WRITE z
8 EMPTY
        """.trimIndent()
    )

    fun `test binary expr`() = checkCfg(
        """
fn main() {
    let x = 12;
    let y = 12;
    let z = x + y;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 DECL y
3 WRITE y
4 DECL z
5 READ x
6 READ y
7 WRITE z
8 EMPTY
        """.trimIndent()
    )

    fun `test index expr`() = checkCfg(
        """
fn main() {
    let arr: [i32; 5] = [1, 2, 3, 4, 5];
    let x = 1;
    arr[x];
}
        """.trimIndent(),
        """
0 DECL arr
1 WRITE arr
2 DECL x
3 WRITE x
4 READ x
5 READ arr
6 EMPTY
        """.trimIndent()
    )

    fun `test return simple`() = checkCfg(
        """
fn main() {
    return;
}
fn foo(a: i32, b: i32){}
        """.trimIndent(),
        """
0 RETURN
1 EMPTY
        """.trimIndent()
    )

    fun `test return with expr`() = checkCfg(
        """
fn main() {
    let x = 12;
    return x;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 READ x
3 RETURN
4 EMPTY
        """.trimIndent()
    )

    fun `test try expr`() = checkCfg(
        """
fn main() {
    let x = 12;
    Ok(x)?;
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 READ x
3 CONDGOTO 5
4 RETURN
5 EMPTY
        """.trimIndent()
    )

    fun `test call diverge`() = checkCfg(
        """
fn main() {
    let x = foo();
}
fn foo() -> !{}
        """.trimIndent(),
        """
0 DECL x
1 DIVERGE
2 WRITE x
3 EMPTY
        """.trimIndent()
    )

    fun `test block`() = checkCfg(
        """
fn main() {
    {
        let x = 12;
    }
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 EMPTY
        """.trimIndent()
    )

    fun `test while`() = checkCfg(
        """
fn main() {
    let x = 12;
    let y = 23;
    while (x < y) {
        x = x + 1;
    }
}
        """.trimIndent(),
        """
0 DECL x
1 WRITE x
2 DECL y
3 WRITE y
4 GOTO 7
5 READ x
6 WRITE x
7 READ x
8 READ y
9 CONDGOTO 5
10 EMPTY
        """.trimIndent()
    )

    private fun checkCfg(@Language("Rust") text: String, cfg: String) {
        myFixture.configureByText("main.rs", text)
        val block = PsiTreeUtil.getChildOfType(myFixture.file as RsFile, RsFunction::class.java)!!.block!!
        val result = ControlFlowBuilder().buildControlFlow(block).toString()
        Assert.assertEquals("Text mismatch:\n", cfg, result)
    }
}
