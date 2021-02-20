/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator

class CargoTestConsoleProperties(
    runconfig: RunConfiguration,
    executor: Executor
) : SMTRunnerConsoleProperties(runconfig, "Cargo Test", executor),
    SMCustomMessagesParsing {

    init {
        isIdBasedTestTree = true
    }

    override fun getTestLocator(): SMTestLocator? = CargoTestLocator

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties
    ): OutputToGeneralTestEventsConverter = CargoTestEventsConverter(testFrameworkName, consoleProperties)
}
