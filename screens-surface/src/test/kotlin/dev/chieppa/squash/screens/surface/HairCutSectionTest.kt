package dev.chieppa.squash.screens.surface

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
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.character.hair.shellPartId
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ShellFade
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.hairShellOn
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val HEAD = PartId("head")

/** The Hair section's cuts: a short-hair shell added, faded and removed, and a group's trim switched on, each in one step. */
@OptIn(ExperimentalTestApi::class)
class HairCutSectionTest {
    private val work = TestWork()
    private val state = AppState(shipped, work).also { it.createCharacter(shipped.plans.first { plan -> plan.id == BodyPlanId("blank") }, "Toy") }
    private val session get() = checkNotNull(state.session.value)
    private fun steps() = session.history.steps.value.done.size

    private fun withHead() {
        val body = checkNotNull(state.selectedPart.value)
        val head = assertNotNull(shipped.template(TemplateId("head-round"))).ref
        session.send(HandSockets.add(body, 1, 1f, 0f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(HEAD, head, SocketRef(body, HandSockets.name(1)), role = Role.HEAD))))
        work.settle(session)
    }

    @Test
    fun aShellIsAddedFadedAndRemovedEachInOneStep() = runComposeUiTest {
        withHead()
        setContent { StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { HairSection(state, session) } } }
        val shell = { session.document.value.character.hairShellOn(HEAD) }
        val before = steps()
        onNodeWithTag(HairShellTags.ADD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "adding a shell is one step")
        assertNotNull(shell())
        onNodeWithTag(HairShellTags.setting("fadeTo")).assertDoesNotExist()
        onNodeWithTag(HairShellTags.fade(ShellFade.MID)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 2, steps(), "a fade is one step")
        assertEquals(ShellFade.MID, shell()?.fade)
        onNodeWithTag(HairShellTags.setting("fadeTo")).assertExists()
        onNodeWithTag(HairShellTags.setting("hairline.front")).assertExists()
        work.settle(session)
        assertNotNull(session.assembly.output.value?.part(shellPartId(HEAD)), "the shell is built over the head")
        onNodeWithTag(HairShellTags.REMOVE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 3, steps(), "removing it is one step")
        assertNull(shell())
    }

    @Test
    fun aPresetCutShortGivesItsShellAndAGroupsTrimSwitchesInOneStep() = runComposeUiTest {
        withHead()
        setContent { StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { HairSection(state, session) } } }
        onNodeWithTag(HairTags.PRESET).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(HairTags.preset(HairPreset.UNDERCUT)).performClick()
        waitForIdle()
        val character = { session.document.value.character }
        assertNotNull(character().hairShellOn(HEAD), "an undercut gives the head its shell")
        assertTrue(character().lockGroups.all { it.mass == null }, "its locks root on the head")
        onNodeWithTag(HairTags.group("top")).performScrollTo().performClick()
        waitForIdle()
        val before = steps()
        onNodeWithTag(HairCutTags.TRIM).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "switching a trim on is one step")
        assertNotNull(character().lockGroups.first { it.id.value == "top" }.trim)
        onNodeWithTag(HairTags.setting("trim.level")).assertExists()
    }
}
