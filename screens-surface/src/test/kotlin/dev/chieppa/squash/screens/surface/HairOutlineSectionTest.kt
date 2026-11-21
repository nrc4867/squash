package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.character.hair.fit.hairFit
import dev.chieppa.squash.character.hair.hairPresetCommand
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.HairOutlineView
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetHairOutline
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import org.junit.jupiter.api.Tag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val HEAD = PartId("head")

/** The Hair section's Outline: Show, Fit, Refit and Remove each one step or none, and Trace waiting on a front reference image. */
@OptIn(ExperimentalTestApi::class)
@Tag("integration")
class HairOutlineSectionTest {
    private val work = TestWork()
    private val state = AppState(shipped, work).also { it.createCharacter(shipped.plans.first { plan -> plan.id == BodyPlanId("blank") }, "Toy") }
    private val session get() = checkNotNull(state.session.value)
    private fun steps() = session.history.steps.value.done.size

    /** A head with the bob's locks, and a round outline with flicks at its lower corners drawn round it. */
    private fun withOutlinedBob() {
        val body = checkNotNull(state.selectedPart.value)
        val head = assertNotNull(shipped.template(TemplateId("head-round"))).ref
        session.send(HandSockets.add(body, 1, 1f, 0f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(HEAD, head, SocketRef(body, HandSockets.name(1)), role = Role.HEAD))))
        work.settle(session)
        session.send(checkNotNull(hairPresetCommand(session.document.value.character, checkNotNull(session.assembly.output.value), HEAD, HairPreset.BOB)))
        work.settle(session)
        val points = List(36) { i ->
            val a = 2.0 * PI * i / 36
            val flick = if (i == 22 || i == 32) 1.25f else 1f
            CurvePoint((0.16f * cos(a).toFloat()) * flick, 0.15f + 0.17f * sin(a).toFloat() * flick)
        }
        session.send(SetHairOutline(HEAD, HairOutline(HEAD, listOf(HairOutlineView(OutlineView.FRONT, points)))))
        work.settle(session)
    }

    @Test
    fun theOutlineIsShownFittedAndRemovedEachInOneStep() = runComposeUiTest {
        withOutlinedBob()
        setContent { StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { HairSection(state, session) } } }
        onNodeWithTag(HairOutlineTags.TRACE).performScrollTo().assertIsNotEnabled()
        val before = steps()
        onNodeWithTag(HairOutlineTags.SHOW).performScrollTo().performClick()
        waitForIdle()
        assertTrue(state.face.hairOutline, "Show outline shows it over the panes")
        assertEquals(before, steps(), "showing it changes nothing in the document")
        val groups = session.document.value.character.lockGroups
        onNodeWithTag(HairOutlineTags.REFIT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "a refit is one step")
        assertNotEquals(groups, session.document.value.character.lockGroups, "a refit sets the outer locks")
        work.settle(session)
        val body = checkNotNull(session.bodyAssembly.output.value)
        assertNull(hairFit(session.document.value.character, body, HEAD)?.command, "a second refit of the same outline changes nothing")
        onNodeWithTag(HairOutlineTags.OFF).performScrollTo().assertExists()
        onNodeWithTag(HairOutlineTags.FIT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 2, steps(), "the fit switch is one step")
        assertEquals(false, session.document.value.character.hairOutlineOn(HEAD)?.fit)
        onNodeWithTag(HairOutlineTags.REMOVE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(before + 3, steps(), "removing it is one step")
        assertNull(session.document.value.character.hairOutlineOn(HEAD))
    }
}
