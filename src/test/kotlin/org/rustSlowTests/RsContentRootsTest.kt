/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.TestProject
import org.rust.cargo.RustWithToolchainTestBase

class RsContentRootsTest : RustWithToolchainTestBase() {

    fun test() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [workspace]
                members = ["subproject"]
            """)
            dir("src") {
                rust("main.rs", "")
            }
            dir("examples") {}
            dir("tests") {}
            dir("benches") {}
            dir("target") {}

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("main.rs", "")
                }
                dir("examples") {}
                dir("tests") {}
                dir("benches") {}
                dir("target") {}
            }
        }

        val projectFolders = listOf(
            ProjectFolder.Source(project.findFile("src"), false),
            ProjectFolder.Source(project.findFile("examples"), false),
            ProjectFolder.Source(project.findFile("tests"), true),
            ProjectFolder.Source(project.findFile("benches"), true),
            ProjectFolder.Excluded(project.findFile("target")),
            ProjectFolder.Source(project.findFile("subproject/src"), false),
            ProjectFolder.Source(project.findFile("subproject/examples"), false),
            ProjectFolder.Source(project.findFile("subproject/tests"), true),
            ProjectFolder.Source(project.findFile("subproject/benches"), true),
            ProjectFolder.Excluded(project.findFile("subproject/target"))
        )

        check(projectFolders)
    }

    private fun check(projectFolders: List<ProjectFolder>) {
        ModuleRootModificationUtil.updateModel(myModule) { model ->
            val contentEntry = model.contentEntries.firstOrNull() ?: error("")
            val sourceFiles = contentEntry.sourceFolders.associateBy { it.file }
            val excludedFiles = contentEntry.excludeFolders.associateBy { it.file }

            for (projectFolder in projectFolders) {
                when (projectFolder) {
                    is ProjectFolder.Source -> {
                        val sourceFile = sourceFiles[projectFolder.file]
                            ?: error("Can't find `${projectFolder.file}` folder in source folders")
                        check(sourceFile.isTestSource == projectFolder.isTest)
                    }
                    is ProjectFolder.Excluded -> {
                        if (excludedFiles[projectFolder.file] == null) {
                            error("Can't find `${projectFolder.file}` folder in excluded folders")
                        }
                    }
                }
            }
        }
    }

    private fun TestProject.findFile(path: String): VirtualFile =
        root.findFileByRelativePath(path) ?: error("Can't find `$path` in `$root`")

    private sealed class ProjectFolder {
        data class Source(val file: VirtualFile, val isTest: Boolean) : ProjectFolder()
        data class Excluded(val file: VirtualFile) : ProjectFolder()
    }
}
