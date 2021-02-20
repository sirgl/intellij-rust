/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.text.SemVer
import junit.framework.AssertionFailedError
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.TestProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.CrateType
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.RustcVersion
import org.rust.cargo.toolchain.Rustup
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.ext.ancestorOrSelf
import java.nio.file.Paths
import java.util.*

abstract class RsTestBase : LightPlatformCodeInsightFixtureTestCase(), RsTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = DefaultDescriptor

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${RsTestCase.testResourcesPath}/$dataPath"

    override fun setUp() {
        super.setUp()
        val testMethod = javaClass.getMethod(name)
        val version = testMethod.getAnnotation(MockRustcVersion::class.java)?.rustcVersion ?: return
        val (semVer, channel) = parse(version)
        val rustcInfo = RustcInfo("", RustcVersion(semVer, "", channel))
        project.cargoProjects.setRustcInfo(rustcInfo)
    }

    private fun parse(version: String): Pair<SemVer, RustChannel> {
        val versionRe = """(\d+\.\d+\.\d+)(.*)""".toRegex()
        val result = versionRe.matchEntire(version) ?: error("$version should match `${versionRe.pattern}` pattern")

        val versionText = result.groups[1]?.value ?: error("")
        val semVer = SemVer.parseFromText(versionText) ?: error("")

        val releaseSuffix = result.groups[2]?.value.orEmpty()
        val channel = when {
            releaseSuffix.isEmpty() -> RustChannel.STABLE
            releaseSuffix.startsWith("-beta") -> RustChannel.BETA
            releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
            else -> RustChannel.DEFAULT
        }
        return semVer to channel
    }

    override fun runTest() {
        val projectDescriptor = projectDescriptor
        val reason = (projectDescriptor as? RustProjectDescriptorBase)?.skipTestReason
        if (reason != null) {
            System.err.println("SKIP $name: $reason")
            return
        }
        super.runTest()
    }

    protected val fileName: String
        get() = "$testName.rs"

    private val testName: String
        get() = camelOrWordsToSnake(getTestName(true))

    protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val (before, after) = (fileName to fileName.replace(".rs", "_after.rs"))
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }

    protected fun checkByDirectory(action: () -> Unit) {
        val (before, after) = ("$testName/before" to "$testName/after")

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action()

        val afterDir = getVirtualFileByName("$testDataPath/$after")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByDirectory(@Language("Rust") before: String, @Language("Rust") after: String, action: () -> Unit) {
        fileTreeFromText(before).create()
        action()
        FileDocumentManager.getInstance().saveAllDocuments()
        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    protected fun checkByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun openFileInEditor(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
        LocalFileSystem.getInstance().findFileByPath(path)

    protected inline fun <reified X : Throwable> expect(f: () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            if (e is X)
                return
            throw e
        }
        fail("No ${X::class.java} was thrown during the test")
    }

    inner class InlineFile(private @Language("Rust") val code: String, val name: String = "main.rs") {
        private val hasCaretMarker = "/*caret*/" in code

        init {
            myFixture.configureByText(name, replaceCaretMarker(code))
        }

        fun withCaret() {
            check(hasCaretMarker) {
                "Please, add `/*caret*/` marker to\n$code"
            }
        }
    }

    protected inline fun <reified T : PsiElement> findElementInEditor(marker: String = "^"): T {
        val (element, data) = findElementWithDataAndOffsetInEditor<T>(marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    protected inline fun <reified T : PsiElement> findElementAndDataInEditor(marker: String = "^"): Pair<T, String> {
        val (element, data) = findElementWithDataAndOffsetInEditor<T>(marker)
        return element to data
    }

    protected inline fun <reified T : PsiElement> findElementWithDataAndOffsetInEditor(marker: String = "^"): Triple<T, String, Int> {
        val caretMarker = "//$marker"
        val (elementAtMarker, data, offset) = run {
            val text = myFixture.file.text
            val markerOffset = text.indexOf(caretMarker)
            check(markerOffset != -1) { "No `$marker` marker:\n$text" }
            check(text.indexOf(caretMarker, startIndex = markerOffset + 1) == -1) {
                "More than one `$marker` marker:\n$text"
            }

            val data = text.drop(markerOffset).removePrefix(caretMarker).takeWhile { it != '\n' }.trim()
            val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
            val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
            val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
            Triple(myFixture.file.findElementAt(elementOffset)!!, data, elementOffset)
        }
        val element = elementAtMarker.ancestorOrSelf<T>()
            ?: error("No ${T::class.java.simpleName} at ${elementAtMarker.text}")
        return Triple(element, data, offset)
    }

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    protected fun reportTeamCityMetric(name: String, value: Long) {
        //https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
        if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            println("##teamcity[buildStatisticValue key='$name' value='$value']")
        } else {
            println("$name: $value")
        }
    }

    protected fun applyQuickFix(name: String) {
        val action = myFixture.findSingleIntention(name)
        myFixture.launchAction(action)
    }

    protected open class RustProjectDescriptorBase : LightProjectDescriptor() {

        open val skipTestReason: String? = null

        open val rustcInfo: RustcInfo? = null

        final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            if (skipTestReason != null) return

            val projectDir = contentEntry.file!!
            val ws = testCargoProject(module, projectDir.url)
            module.project.cargoProjects.createTestProject(projectDir, ws, rustcInfo)
        }

        open fun setUp(fixture: CodeInsightTestFixture) {}

        open fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
            val packages = listOf(testCargoPackage(contentRoot))
            return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, emptyMap()))
        }

        protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = CargoWorkspaceData.Package(
            id = "$name 0.0.1",
            contentRootUrl = contentRoot,
            name = name,
            version = "0.0.1",
            targets = listOf(
                CargoWorkspaceData.Target("$contentRoot/main.rs", name, TargetKind.BIN, listOf(CrateType.BIN)),
                CargoWorkspaceData.Target("$contentRoot/lib.rs", name, TargetKind.LIB, listOf(CrateType.LIB))
            ),
            source = null,
            origin = PackageOrigin.WORKSPACE
        )
    }

    protected open class WithRustup(private val delegate: RustProjectDescriptorBase) : RustProjectDescriptorBase() {
        private val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }

        private val rustup by lazy { toolchain?.rustup(Paths.get(".")) }
        val stdlib by lazy { (rustup?.downloadStdlib() as? Rustup.DownloadResult.Ok)?.library }

        override val skipTestReason: String?
            get() {
                if (rustup == null) return "No rustup"
                if (stdlib == null) return "No stdib"
                return null
            }

        override val rustcInfo: RustcInfo?
            get() {
                val toolchain = toolchain ?: return null
                val sysroot = toolchain.getSysroot(Paths.get(".")) ?: return null
                val rustcVersion = toolchain.queryVersions().rustc
                return RustcInfo(sysroot, rustcVersion)
            }

        override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
            val stdlib = StandardLibrary.fromFile(stdlib!!)!!
            return delegate.testCargoProject(module, contentRoot).withStdlib(stdlib)
        }

        override fun setUp(fixture: CodeInsightTestFixture) {
            delegate.setUp(fixture)
        }
    }

    protected object DefaultDescriptor : RustProjectDescriptorBase()

    protected object WithDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
        private fun externalPackage(contentRoot: String, source: String?, name: String, targetName: String = name): CargoWorkspaceData.Package {
            return CargoWorkspaceData.Package(
                id = "$name 0.0.1",
                contentRootUrl = "",
                name = name,
                version = "0.0.1",
                targets = listOf(
                    // don't use `FileUtil.join` here because it uses `File.separator`
                    // which is system dependent although all other code uses `/` as separator
                    CargoWorkspaceData.Target(source?.let { "$contentRoot/$it" } ?: "", targetName, TargetKind.LIB, listOf(CrateType.BIN))
                ),
                source = source,
                origin = PackageOrigin.DEPENDENCY
            )
        }

        override fun setUp(fixture: CodeInsightTestFixture) {
            val root = fixture.findFileInTempDir(".")!!
            for (source in listOf("dep-lib/lib.rs", "trans-lib/lib.rs")) {
                VfsTestUtil.createFile(root, source)
            }
        }

        override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
            val packages = listOf(
                testCargoPackage(contentRoot),
                externalPackage(contentRoot, "dep-lib/lib.rs", "dep-lib", "dep-lib-target"),
                externalPackage(contentRoot, null, "nosrc-lib", "nosrc-lib-target"),
                externalPackage(contentRoot, "trans-lib/lib.rs", "trans-lib"))

            val depNodes = ArrayList<CargoWorkspaceData.DependencyNode>()
            depNodes.add(CargoWorkspaceData.DependencyNode(0, listOf(1, 2)))   // Our package depends on dep_lib and dep_nosrc_lib

            return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
                packages[0].id to setOf(packages[1].id, packages[2].id)
            )))
        }
    }

    protected object WithStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor)

    protected object WithStdlibAndDependencyRustProjectDescriptor : WithRustup(WithDependencyRustProjectDescriptor)

    protected fun checkAstNotLoaded(fileFilter: VirtualFileFilter) {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileFilter, testRootDisposable)
    }

    protected open fun configureByText(text: String) {
        InlineFile(text.trimIndent())
    }

    protected open fun configureByFileTree(text: String) {
        fileTreeFromText(text).createAndOpenFileWithCaretMarker()
    }

    companion object {
        // XXX: hides `Assert.fail`
        fun fail(message: String): Nothing {
            throw AssertionFailedError(message)
        }

        @JvmStatic
        fun camelOrWordsToSnake(name: String): String {
            if (' ' in name) return name.trim().replace(" ", "_")

            return name.split("(?=[A-Z])".toRegex()).joinToString("_", transform = String::toLowerCase)
        }

        @JvmStatic
        fun checkHtmlStyle(html: String) {
            // http://stackoverflow.com/a/1732454
            val re = "<body>(.*)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val body = (re.find(html)?.let { it.groups[1]!!.value } ?: html).trim()
            check(body[0].isUpperCase()) {
                "Please start description with the capital latter"
            }

            check(body.last() == '.') {
                "Please end description with a period"
            }
        }

        @JvmStatic
        fun getResourceAsString(path: String): String? {
            val stream = RsTestBase::class.java.classLoader.getResourceAsStream(path)
                ?: return null

            return StreamUtil.readText(stream, Charsets.UTF_8)
        }
    }

    protected fun FileTree.create(): TestProject =
        create(myFixture.project, myFixture.findFileInTempDir("."))

    protected fun FileTree.createAndOpenFileWithCaretMarker(): TestProject {
        val testProject = create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        return testProject
    }

    protected val PsiElement.lineNumber: Int
        get() = myFixture.getDocument(myFixture.file).getLineNumber(textOffset)
}

