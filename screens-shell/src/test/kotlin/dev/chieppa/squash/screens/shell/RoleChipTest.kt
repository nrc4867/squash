package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.FRONT_PAW_R
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.uidesign.Tentative
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The role chip in Shape's inspector and the guessed role's chip in Parts. */
@OptIn(ExperimentalTestApi::class)
class RoleChipTest {
    private val state = AppState(shipped).also { it.open(fennecDocument()) }
    private val session get() = checkNotNull(state.session.value)
    private fun part(id: PartId) = checkNotNull(session.document.value.character.part(id))

    private fun tentative(value: Boolean) = SemanticsMatcher.expectValue(Tentative, value)

    @Test
    fun theChipReadsTheRoleAndItsListSetsAnotherForThePair() = runComposeUiTest {
        state.selectedPart.value = FRONT_PAW_L
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.ROLE_CHIP).assert(tentative(false))
        onNodeWithText("Front paw").assertExists()
        onNodeWithTag(ShellTags.ROLE_CHIP).performClick()
        waitForIdle()
        onNodeWithText("Wing").assertExists()
        onNodeWithText("Foot").performClick()
        waitForIdle()
        assertEquals(Role.FOOT, part(FRONT_PAW_L).role)
        assertEquals(Role.FOOT, part(FRONT_PAW_R).role, "a pair shares its role")
        onNodeWithText("Foot").assertExists()
        session.history.undo()
        waitForIdle()
        assertEquals(Role.HAND, part(FRONT_PAW_R).role, "one undo step")
        onNodeWithText("Front paw").assertExists()
    }

    @Test
    fun aGuessDrawsDashedUntilItIsChosen() = runComposeUiTest {
        session.send(SetRole(TAIL, Role.TAIL, guessed = true))
        state.selectedPart.value = TAIL
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.ROLE_CHIP).assert(tentative(true))
        onNodeWithTag(ShellTags.ROLE_CHIP).performClick()
        waitForIdle()
        onAllNodesWithTag(ShellTags.ROLE_CHIP).assertCountEquals(1)
        onNode(hasText("Tail") and !hasTestTag(ShellTags.ROLE_CHIP)).performClick()
        waitForIdle()
        assertFalse(part(TAIL).roleGuessed, "choosing the guessed role confirms it")
        onNodeWithTag(ShellTags.ROLE_CHIP).assert(tentative(false))
    }

    @Test
    fun partsShowsADashedChipOnAGuessedPartAlone() = runComposeUiTest {
        setContent { StretchTheme(StretchThemes.Ink) { PartsDrawerContent(state) } }
        onAllNodesWithTag(ShellTags.partRowRole("tail")).assertCountEquals(0)
        session.send(SetRole(TAIL, Role.TAIL, guessed = true))
        waitForIdle()
        onNodeWithTag(ShellTags.partRowRole("tail")).assert(tentative(true))
        onNodeWithText("Tail?").assertExists()
        onAllNodesWithTag(ShellTags.partRowRole(TORSO.value)).assertCountEquals(0)
    }
}
