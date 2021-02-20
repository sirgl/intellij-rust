/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties
import org.rust.cargo.toolchain.CargoCommandLine

class CargoTestRunState(
    environment: ExecutionEnvironment,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunState(environment, config) {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()

        val consoleProperties = CargoTestConsoleProperties(environment.runProfile as RunConfiguration, executor)
        val consoleView = SMTestRunnerConnectionUtil
            .createAndAttachConsole("Cargo Test", processHandler, consoleProperties) as SMTRunnerConsoleView
        createFilters().forEach { consoleView.addMessageFilter(it) }

        return DefaultExecutionResult(consoleView, processHandler)
            .apply { setRestartActions(ToggleAutoTestAction()) }
    }

    override fun prepareCommandLine(): CargoCommandLine =
        commandLine.copy(additionalArguments = patchArgs(commandLine))

    companion object {
        @VisibleForTesting
        fun patchArgs(cmd: CargoCommandLine): List<String> {
            val (cargoArgs, libtestArgs) = cmd.splitOnDoubleDash()
                .let { it.first.toMutableList() to it.second.toMutableList() }

            val noFailFast = "--no-fail-fast"

            if (noFailFast !in cargoArgs) {
                cargoArgs.add(noFailFast)
            }

            val noFormatJson = "--format=json"
            val format = "--format"
            val noUnstableOptions = "-Z"

            if (noUnstableOptions !in libtestArgs) {
                libtestArgs.add(noUnstableOptions)
                libtestArgs.add("unstable-options")
            }
            val idx = libtestArgs.indexOf("--format")
            val indexArgWithValue = libtestArgs.indexOfFirst { it.startsWith(format) }
            if (idx != -1) {
                if (idx < libtestArgs.size - 1) {
                    if (!libtestArgs[idx + 1].startsWith("-")) {
                        libtestArgs[idx + 1] = "json"
                    } else {
                        libtestArgs.add(idx + 1, "json")
                    }
                } else {
                    libtestArgs.add("json")
                }
            } else if (indexArgWithValue != -1) {
                libtestArgs[indexArgWithValue] = "--format=json"
            } else {
                libtestArgs.add(noFormatJson)
            }

            return cargoArgs + "--" + libtestArgs
        }
    }
}
