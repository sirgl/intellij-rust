/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

class RunConfigurationProducerTest : RunConfigurationProducerTestBase() {
    fun `test main fn is more specific than test fn`() {
        testProject {
            bin("foo", "src/main.rs", """
                #[test]
                fn main() { /*caret*/ }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main fn is more specific than bench fn`() {
        testProject {
            bin("foo", "src/main.rs", """
                #[bench]
                fn main() { /*caret*/ }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main fn is more specific than test mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() { /*caret*/ }
                fn foo() {}
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main fn is more specific than bench mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() { /*caret*/ }
                fn foo() {}
                #[bench]
                fn bench_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test test fn is more specific than main mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() {}
                fn foo() {}
                #[test]
                fn test_foo() { /*caret*/ }
                #[bench]
                fn bench_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test bench fn is more specific than main or test mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() {}
                fn foo() {}
                #[test]
                fn test_foo() {}
                #[bench]
                fn bench_foo() { /*caret*/ }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test test mod is more specific than bench mod`() {
        testProject {
            lib("foo", "src/foo.rs", """
                fn foo() { /*caret*/ }
                #[test]
                fn test_foo() {}
                #[bench]
                fn bench_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main mod is more specific than test mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() {}
                fn foo() { /*caret*/ }
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test hyphen in name works`() {
        testProject {
            example("hello-world", "example/hello.rs").open()
        }
        checkOnLeaf()
    }
}
