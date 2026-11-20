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
import dev.chieppa.squash.character.hair.presetGroupNames
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val HEAD = PartId("head")

/** The Face inspector's Hair section: a preset gives the head its lock groups in one step, a group is picked, and removed in one step. */
@OptIn(ExperimentalTestApi::class)
class HairSectionTest {
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
    fun aPresetGivesTheHeadItsGroupsInOneStepAndAGroupIsRemovedInOne() = runComposeUiTest {
        withHead()
        setContent { StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { HairSection(session) } } }
        onNodeWithTag(HairTags.PRESET).performScrollTo().performClick()
        waitForIdle()
        val before = steps()
        onNodeWithTag(HairTags.preset(HairPreset.BOB)).performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "a preset is one step")
        val groups = session.document.value.character.lockGroups
        assertEquals(presetGroupNames(HairPreset.BOB), groups.map { it.id.value })
        assertTrue(groups.all { it.on == HEAD })
        assertTrue(session.document.value.character.colliders.isNotEmpty(), "the body's collision shapes are set up with the first preset")

        onNodeWithTag(HairTags.group("fringe")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(HairTags.setting("count")).assertExists()
        onNodeWithTag(HairTags.setting("curl")).assertExists()
        onNodeWithTag(HairTags.REMOVE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 2, steps(), "removing a group is one step")
        assertTrue(session.document.value.character.lockGroups.none { it.id.value == "fringe" })
        work.settle(session)
        assertNotNull(session.assembly.output.value?.part(lockGroupPartId(groups.first().id)), "the groups left are built")
    }
}
