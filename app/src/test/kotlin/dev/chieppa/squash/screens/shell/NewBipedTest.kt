package dev.chieppa.squash.screens.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object NoBipedFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** New character's Biped row: between Bird and Blank, read as two legs, two arms, upright; creating from it gives an upright torso. */
@OptIn(ExperimentalTestApi::class)
class NewBipedTest {
    private val work = TestWork()

    @Test
    fun bipedStartsACharacterWithAnUprightTorso() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        setStretchContent { SquashApp(state, NoBipedFiles) }
        assertEquals(listOf("canine-quadruped", "bird", "biped", "blank"), shipped.plans.map { it.id.value })
        val rows = listOf("canine-quadruped", "bird", "biped", "blank").map { onNodeWithTag(ShellTags.newPlan(it)).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(rows.sorted(), rows, "the rows read Canine quadruped, Bird, Biped, Blank")
        onNode(hasTestTag(ShellTags.newPlan("biped")) and hasText("Two legs, two arms, upright", substring = true, ignoreCase = false)).assertExists()
        onNodeWithTag(ShellTags.newPlan("biped")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Person")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
        val document = assertNotNull(state.session.value).document.value
        val torso = document.character.parts.single()
        assertEquals(TemplateId("torso-upright"), torso.template.id)
        assertEquals(Role.BODY, torso.role)
        val plan = shipped.plans.single { it.id.value == "biped" }
        assertEquals(setOf("neck", "shoulder", "hip"), openSockets(document, plan, shipped).map { it.name }.toSet())
        assertTrue(openSockets(document, plan, shipped).single { it.name == "shoulder" }.paired)
    }
}
