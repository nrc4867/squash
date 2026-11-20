package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.membranePartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val ARM = PartId("arm")
private val LEG = PartId("leg")

/** The Shape inspector's membranes: added from a part to another in one step, selected, tuned and removed. */
@OptIn(ExperimentalTestApi::class)
class MembraneSectionTest {
    private val work = TestWork()
    private val state = AppState(shipped, work).also { it.createCharacter(shipped.plans.first { plan -> plan.id == BodyPlanId("blank") }, "Glider") }
    private val session get() = checkNotNull(state.session.value)
    private val body = checkNotNull(state.selectedPart.value)
    private fun steps() = session.history.steps.value.done.size

    private fun inspector() = @androidx.compose.runtime.Composable {
        StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { ShapeInspector(state) { _, _, _, _, _ -> } } }
    }

    private fun twoTubes() {
        val tube = assertNotNull(shipped.template(TemplateId("tube"))).ref
        session.send(HandSockets.add(body, 1, 0.8f, 0.25f, 0f, paired = false))
        session.send(HandSockets.add(body, 2, 0.2f, 0.25f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(ARM, tube, SocketRef(body, HandSockets.name(1))), PartInstance(LEG, tube, SocketRef(body, HandSockets.name(2))))))
        work.settle(session)
    }

    @Test
    fun addMembraneSpansToTheChosenPartInOneStepAndSelectsIt() = runComposeUiTest {
        twoTubes()
        state.selectedPart.value = ARM
        setContent(inspector())
        onNodeWithTag(ShellTags.MEMBRANE_ADD).performScrollTo().performClick()
        waitForIdle()
        val before = steps()
        onNodeWithTag(ShellTags.membraneTo(LEG.value)).performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "adding a membrane is one step")
        val membrane = session.document.value.character.membranes.single()
        assertEquals(ARM, membrane.first.single().part)
        assertEquals(LEG, membrane.second.single().part)
        assertEquals(membranePartId(membrane.id), state.selectedPart.value, "the new membrane is selected")
        onNodeWithTag(ShellTags.membraneSetting("edge")).assertExists()
        work.settle(session)
        assertNotNull(session.assembly.output.value?.part(membranePartId(membrane.id)), "the membrane is built")
    }

    @Test
    fun aSelectedMembraneTogglesBothSidesAndIsRemovedInOneStep() = runComposeUiTest {
        twoTubes()
        state.selectedPart.value = ARM
        setContent(inspector())
        onNodeWithTag(ShellTags.MEMBRANE_ADD).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.membraneTo(LEG.value)).performClick()
        waitForIdle()
        val id = session.document.value.character.membranes.single().id
        onNodeWithTag(ShellTags.MEMBRANE_MIRRORED).performScrollTo().performClick()
        waitForIdle()
        assertTrue(session.document.value.character.membranes.single().mirrored)
        val before = steps()
        onNodeWithTag(ShellTags.MEMBRANE_REMOVE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 1, steps())
        assertTrue(session.document.value.character.membranes.none { it.id == id })
        assertEquals(ARM, state.selectedPart.value, "removing it selects the part it started on")
    }
}
