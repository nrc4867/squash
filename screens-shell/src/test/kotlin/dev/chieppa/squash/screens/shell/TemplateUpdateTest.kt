package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.UpdateFromLibrary
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals

private const val EARLESS = 3

/** Update from library in the Template menu, offered only over an older version, and what a version leaves behind highlighted in place. See HLD: data-model, Update from library. */
@OptIn(ExperimentalTestApi::class)
class TemplateUpdateTest {
    private val work = TestWork()

    private fun inspected(state: AppState) = @androidx.compose.runtime.Composable {
        StretchTheme(StretchThemes.Ink) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PartsDrawerContent(state)
                ShapeInspector(state) { _, _, _, _, _ -> }
            }
        }
    }

    @Test
    fun theTemplateMenuUpdatesAnOlderTorsoInOneStepAndThenOffersNoUpdate() = runComposeUiTest {
        val state = AppState(shipped, work).also { it.open(fennecDocument()) }
        val session = checkNotNull(state.session.value)
        session.send(UpdateFromLibrary(TORSO, 1))
        state.selectedPart.value = TORSO
        setContent(inspected(state))
        onNodeWithTag(ShellTags.TEMPLATE_MENU).performScrollTo().performClick()
        waitForIdle()
        val before = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.TEMPLATE_UPDATE).performClick()
        waitForIdle()
        assertEquals(2, session.document.value.character.part(TORSO)?.template?.version)
        assertEquals(before + 1, session.history.steps.value.done.size, "one undo step")
        onNodeWithTag(ShellTags.TEMPLATE_MENU).assertDoesNotExist()
    }

    @Test
    fun earsOnASocketTheNewHeadLacksAndItsPosedNeckAreHighlightedUntilUndone() = runComposeUiTest {
        val head = checkNotNull(shipped.template(TemplateId("head")))
        val earless = head.copy(version = EARLESS, blocks = head.blocks.filter { block -> block.sockets().none { it.name == "ear" } && block.joints().none { it.name == "neck" } })
        val state = AppState(shipped.copy(parts = shipped.parts + earless), work).also { it.open(fennecDocument()) }
        val session = checkNotNull(state.session.value)
        session.send(SetBoneAngle(HEAD, "neck", 20f))
        state.selectedPart.value = HEAD
        setContent(inspected(state))
        onNodeWithTag(ShellTags.partRowUnseated("ear")).assertDoesNotExist()
        onNodeWithTag(ShellTags.anatomyMissing("neck")).assertDoesNotExist()
        onNodeWithTag(ShellTags.TEMPLATE_MENU).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.TEMPLATE_UPDATE).performClick()
        waitForIdle()
        assertEquals(EARLESS, session.document.value.character.part(HEAD)?.template?.version)
        onNodeWithTag(ShellTags.partRowUnseated("ear")).assertExists()
        onNodeWithTag(ShellTags.anatomyMissing("neck")).assertExists()
        session.history.undo()
        waitForIdle()
        onNodeWithTag(ShellTags.partRowUnseated("ear")).assertDoesNotExist()
        onNodeWithTag(ShellTags.anatomyMissing("neck")).assertDoesNotExist()
    }
}
