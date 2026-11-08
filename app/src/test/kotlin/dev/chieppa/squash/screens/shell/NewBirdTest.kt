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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object NoBirdFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** New character's Bird row: after Canine quadruped, with a perched bird and Wings, two legs, a beak; creating from it gives a perched torso. */
@OptIn(ExperimentalTestApi::class)
class NewBirdTest {
    private val work = TestWork()

    @AfterTest
    fun stopWork() = work.close()

    @Test
    fun birdStartsACharacterWithAPerchedTorso() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        setStretchContent { SquashApp(state, NoBirdFiles) }
        val rows = listOf("canine-quadruped", "bird", "biped").map { onNodeWithTag(ShellTags.newPlan(it)).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(rows.sorted(), rows, "Bird reads after Canine quadruped and before Biped")
        onNode(hasTestTag(ShellTags.newPlan("bird")) and hasText("Wings, two legs, a beak", substring = true, ignoreCase = false)).assertExists()
        onNodeWithTag(ShellTags.newPlan("bird")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Toucan")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
        val document = assertNotNull(state.session.value).document.value
        val torso = document.character.parts.single()
        assertEquals(TemplateId("torso-bird"), torso.template.id)
        assertEquals(Role.BODY, torso.role)
        val plan = shipped.plans.single { it.id.value == "bird" }
        assertEquals(setOf("neck", "shoulder", "hip", "tail"), openSockets(document, plan, shipped).map { it.name }.toSet())
        assertTrue(openSockets(document, plan, shipped).single { it.name == "shoulder" }.paired)
        assertTrue(openSockets(document, plan, shipped).single { it.name == "hip" }.paired)
    }
}
