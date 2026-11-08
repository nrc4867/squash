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

private object NoFishFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** New character's Fish row: after Biped, with a fish and Fins, a tail, two eyes; creating from it gives one undrawn tube body with the fish's sockets open. */
@OptIn(ExperimentalTestApi::class)
class NewFishTest {
    private val work = TestWork()

    @Test
    fun fishStartsACharacterWithATubeBody() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        setStretchContent { SquashApp(state, NoFishFiles) }
        val rows = listOf("canine-quadruped", "bird", "biped", "fish", "blank").map { onNodeWithTag(ShellTags.newPlan(it)).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(rows.sorted(), rows, "Fish reads after Biped and before Blank")
        onNode(hasTestTag(ShellTags.newPlan("fish")) and hasText("Fins, a tail, two eyes", substring = true, ignoreCase = false)).assertExists()
        onNodeWithTag(ShellTags.newPlan("fish")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Koi")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
        val document = assertNotNull(state.session.value).document.value
        val body = document.character.parts.single()
        assertEquals(TemplateId("tube"), body.template.id)
        assertEquals(Role.BODY, body.role)
        val plan = shipped.plans.single { it.id.value == "fish" }
        assertEquals(setOf("dorsal", "tail", "pectoral", "anal", "eye"), openSockets(document, plan, shipped).map { it.name }.toSet())
        assertTrue(openSockets(document, plan, shipped).single { it.name == "pectoral" }.paired)
        assertTrue(openSockets(document, plan, shipped).single { it.name == "eye" }.paired)
    }
}
