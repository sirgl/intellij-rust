/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.fileTree

class CargoFmtTest : RustWithToolchainTestBase() {

    fun `test cargo fmt`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                    println!("Hello, world!");
                    }
                """)
            }
        }.create()

        val main = cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!
        reformat(main)
    }

    fun `test save document before rustfmt execution`() {
        val fileWithCaret = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {/*caret*/
                        println!("Hello, world!");
                    }
                """)
            }
        }.create().fileWithCaret

        val file = myFixture.configureFromTempProjectFile(fileWithCaret).virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val prevText = document.text
        myFixture.type("\n\n\n")

        reformat(file)
        assertEquals(prevText.trim(), document.text.trim())
    }

    private fun reformat(file: VirtualFile) {
        val cargo = project.toolchain!!.rawCargo()
        val result = cargo.reformatFile(testRootDisposable, file)
        check(result.exitCode == 0)
    }
}
