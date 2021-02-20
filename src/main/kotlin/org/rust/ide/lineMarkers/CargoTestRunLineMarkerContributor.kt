/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoBenchRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestLocator
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.elementType
import javax.swing.Icon

class CargoTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType != IDENTIFIER) return null
        val parent = element.parent
        if (parent is RsFunction && CargoExecutableRunConfigurationProducer.isMainFunction(parent)) return null

        val state = CargoTestRunConfigurationProducer().findConfig(arrayOf(parent), climbUp = false)
            ?: CargoBenchRunConfigurationProducer().findConfig(arrayOf(parent), climbUp = false)
            ?: return null
        val icon = if (state.commandName == "test") {
            getTestStateIcon(state.sourceElement)
        } else {
            AllIcons.RunConfigurations.TestState.Run
        }
        return Info(
            icon,
            Function<PsiElement, String> { state.configurationName },
            *ExecutorAction.getActions(1)
        )
    }

    companion object {
        fun getTestStateIcon(sourceElement: PsiElement): Icon? {
            val url = when (sourceElement) {
                is RsFunction -> CargoTestLocator.getTestFnUrl(sourceElement)
                is RsMod -> CargoTestLocator.getTestModUrl(sourceElement)
                else -> return null
            }

            val project = sourceElement.project

            val magnitude = TestStateStorage.getInstance(project).getState(url)
                ?.let { TestIconMapper.getMagnitude(it.magnitude) }

            return when (magnitude) {
                TestStateInfo.Magnitude.PASSED_INDEX,
                TestStateInfo.Magnitude.COMPLETE_INDEX ->
                    CargoIcons.TEST_GREEN

                TestStateInfo.Magnitude.ERROR_INDEX,
                TestStateInfo.Magnitude.FAILED_INDEX ->
                    CargoIcons.TEST_RED

                else -> CargoIcons.TEST
            }
        }
    }
}
