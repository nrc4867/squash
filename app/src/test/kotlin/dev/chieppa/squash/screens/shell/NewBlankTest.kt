package dev.chieppa.squash.screens.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

private object NoBlankFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** New character's Blank row: after the species plans, read as one tube, and creating from it gives one body part and no sockets. */
@OptIn(ExperimentalTestApi::class)
class NewBlankTest {
    private val work = TestWork()

    @Test
    fun blankStartsACharacterWithOneBodyTube() = runDesktopComposeUiTest(1600, 900) {
        val state = AppState(shipped, work)
        setStretchContent { SquashApp(state, NoBlankFiles) }
        onNodeWithTag(ShellTags.newPlan("canine-quadruped")).assertExists()
        onNode(hasTestTag(ShellTags.newPlan("blank")) and hasText("One tube, anatomy by hand", substring = true, ignoreCase = false), useUnmergedTree = false)
            .assertExists()
        onNodeWithTag(ShellTags.newPlan("blank")).performClick()
        waitForIdle()
        onNodeWithText("Body").assertExists()
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Koi")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        assertEquals(Screen.WORKSPACE, state.screen.value)
        val document = assertNotNull(state.session.value).document.value
        val body = document.character.parts.single()
        assertEquals(TemplateId("tube"), body.template.id)
        assertEquals(Role.BODY, body.role)
        assertFalse(body.roleGuessed)
        assertEquals(emptyList(), openSockets(document, blankPlan, shipped))
        onNodeWithTag(ShellTags.partRow("tube")).assertExists()
    }
}
