package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val FIN = PartId("fin")

/** Shape's Generic section: a generic part's section, views, rings and segments, each one command, and nothing for a typed part. */
@OptIn(ExperimentalTestApi::class)
class GenericSectionTest {
    private val state = AppState(shipped).also { state ->
        val new = newCharacter(blankPlan, shipped, "Koi") { "body" }
        val body = PartId("body")
        val fin = PartInstance(FIN, TemplateRef(TemplateId("sheet"), 1), SocketRef(body, HandSockets.name(1)), role = Role.FIN)
        val commands = new.commands + HandSockets.add(body, 1, 0.5f, 0.02f, 0f, paired = false) + AddParts(listOf(fin))
        state.open(commands.fold(new.document) { document, command -> command.apply(document).document })
    }
    private val session get() = checkNotNull(state.session.value)
    private fun param(name: String) = session.document.value.character.let { it.resolved(checkNotNull(it.part(FIN))).params[name] }

    @Test
    fun aSectionIsOneCommand() = runComposeUiTest {
        state.selectedPart.value = FIN
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.GENERIC).assertExists()
        onNodeWithText("Egg").performClick()
        waitForIdle()
        assertEquals(GenericParams.PROFILES.indexOf(SectionProfile.EGG).toFloat(), param(GenericParams.SECTION))
        session.history.undo()
        assertNull(param(GenericParams.SECTION), "one undo step")
    }

    @Test
    fun aViewTurnsOnAndTheLastOneOnStays() = runComposeUiTest {
        state.selectedPart.value = FIN
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.genericView(View.FRONT.name)).assertIsOff()
        onNodeWithTag(ShellTags.genericView(View.FRONT.name)).performClick()
        waitForIdle()
        assertEquals(1f, param(GenericParams.view(View.FRONT)))
        onNodeWithTag(ShellTags.genericView(View.FRONT.name)).assertIsOn()
        session.history.undo()
        assertNull(param(GenericParams.view(View.FRONT)), "one undo step")
        waitForIdle()
        onNodeWithTag(ShellTags.genericView(View.SIDE.name)).performClick()
        waitForIdle()
        assertEquals(0f, param(GenericParams.view(View.SIDE)))
        onNodeWithTag(ShellTags.genericView(View.TOP.name)).performClick()
        waitForIdle()
        assertNull(param(GenericParams.view(View.TOP)), "the last view on stays on")
        onNodeWithTag(ShellTags.genericView(View.TOP.name)).assertIsOn()
    }

    @Test
    fun ringsAndSegmentsAreEachOneCommand() = runComposeUiTest {
        state.selectedPart.value = FIN
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.GENERIC_RINGS).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.GENERIC_RINGS).performTextReplacement("40")
        onNodeWithTag(ShellTags.GENERIC_RINGS).performImeAction()
        waitForIdle()
        assertEquals(40f, param(GenericParams.RINGS))
        onNodeWithTag(ShellTags.GENERIC_SEGMENTS).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.GENERIC_SEGMENTS).performTextReplacement("8")
        onNodeWithTag(ShellTags.GENERIC_SEGMENTS).performImeAction()
        waitForIdle()
        assertEquals(8f, param(GenericParams.SEGMENTS))
        session.history.undo()
        assertNull(param(GenericParams.SEGMENTS))
        assertEquals(40f, param(GenericParams.RINGS), "each change is its own undo step")
    }

    @Test
    fun aTypedPartShowsNoGenericSection() = runComposeUiTest {
        state.open(fennecDocument())
        state.selectedPart.value = TORSO
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.ROLE_CHIP).assertExists()
        onNodeWithTag(ShellTags.GENERIC).assertDoesNotExist()
    }
}
