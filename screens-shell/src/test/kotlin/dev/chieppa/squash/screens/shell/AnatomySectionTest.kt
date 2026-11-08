package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.withSaved
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.addJoint
import dev.chieppa.squash.screens.addLandmark
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.move.JointRef
import dev.chieppa.squash.screens.move.MoveMode
import dev.chieppa.squash.screens.pairCandidates
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LEG_A = PartId("leg-a")
private val LEG_B = PartId("leg-b")

/** Move's four modes with Add joint's fields, the Anatomy section, Pair with… and Save as template's dialog. See board W12. */
@OptIn(ExperimentalTestApi::class)
class AnatomySectionTest {
    private val work = TestWork()

    private val saved = Files.createTempDirectory("saved-templates")
    private val state = AppState(shipped.withSaved(saved), work).also {
        it.createCharacter(shipped.plans.first { plan -> plan.id == BodyPlanId("blank") }, "Lizard")
        it.tool.value = BuildTool.MOVE
    }
    private val session get() = checkNotNull(state.session.value)
    private val body = checkNotNull(state.selectedPart.value)
    private fun params(part: PartId) = session.document.value.character.let { it.resolvedShape(checkNotNull(it.part(part))).params }
    private fun steps() = session.history.steps.value.done.size

    private fun inspector() = @androidx.compose.runtime.Composable {
        StretchTheme(StretchThemes.Ink) { Column(Modifier.verticalScroll(rememberScrollState())) { ShapeInspector(state) { _, _, _, _, _ -> } } }
    }

    @Test
    fun theGridChoosesAddJointWithItsHingeLimitsStretchAndDelete() = runComposeUiTest {
        setContent(inspector())
        listOf("Place part", "Add socket", "Add joint", "Add landmark").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("Add joint").performClick()
        waitForIdle()
        assertEquals(MoveMode.ADD_JOINT, state.move.mode.value)
        onNodeWithTag(ShellTags.JOINT_LOW).assertTextContains("-40", substring = true)
        onNodeWithTag(ShellTags.JOINT_HIGH).assertTextContains("40", substring = true)
        onNodeWithTag(ShellTags.JOINT_DELETE).assertIsNotEnabled()
        onNodeWithText("Y").performClick()
        onNodeWithTag(ShellTags.JOINT_STRETCH).performClick()
        waitForIdle()
        assertEquals(Hinge.Y, state.move.hinge.value, "with none picked the hinge is the next joint's")
        assertTrue(state.move.stretch.value)
        state.addJoint(body, 0.35f)
        waitForIdle()
        assertEquals(1f, params(body)[HandJoints.hinge(1)], "the next joint takes the fields")
        onNodeWithTag(ShellTags.JOINT_DELETE).assertIsEnabled()
        onNodeWithText("Z").performClick()
        waitForIdle()
        assertEquals(2f, params(body)[HandJoints.hinge(1)], "with one picked the fields edit it")
        val before = steps()
        onNodeWithTag(ShellTags.JOINT_DELETE).performClick()
        waitForIdle()
        assertEquals(before + 1, steps())
        assertEquals(emptyList(), HandJoints.ids(params(body)))
    }

    @Test
    fun anatomyListsJointsAndLandmarksAndDeletesThoseMadeByHand() = runComposeUiTest {
        state.addJoint(body, 0.35f)
        state.addJoint(body, 0.7f)
        state.addLandmark(body, 0.9f, 0.2f)
        setContent(inspector())
        onNodeWithTag(ShellTags.ANATOMY_JOINTS).assertTextContains("4")
        onNodeWithTag(ShellTags.ANATOMY_LANDMARKS).assertTextContains("1")
        onNodeWithTag(ShellTags.anatomyRow(HandJoints.name(1))).assertTextContains("Joint 1", substring = true)
        onNodeWithTag(ShellTags.anatomyRow(HandJoints.name(1))).assertTextContains("0.35", substring = true)
        onNodeWithTag(ShellTags.anatomyDelete("base")).assertDoesNotExist()
        onNodeWithTag(ShellTags.anatomyRow(HandJoints.name(2))).performClick()
        waitForIdle()
        assertEquals(JointRef(body, HandJoints.name(2)), state.move.pickedJoint.value, "a row picks its joint")
        onNodeWithTag(ShellTags.anatomyDelete(HandJoints.name(1))).performClick()
        onNodeWithTag(ShellTags.anatomyDelete(HandLandmarks.name(1))).performClick()
        waitForIdle()
        assertEquals(listOf(2), HandJoints.ids(params(body)))
        assertEquals(emptyList(), HandLandmarks.ids(params(body)))
        onNodeWithTag(ShellTags.ANATOMY_LANDMARKS).assertTextContains("0")
    }

    @Test
    fun pairWithOffersTheOtherLegAndUnpairIsItsLastItem() = runComposeUiTest {
        val tube = assertNotNull(shipped.template(TemplateId("tube"))).ref
        session.send(HandSockets.add(body, 1, 0.3f, 0.35f, 0f, paired = false))
        session.send(HandSockets.add(body, 2, 0.3f, 0.65f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(LEG_A, tube, SocketRef(body, HandSockets.name(1))), PartInstance(LEG_B, tube, SocketRef(body, HandSockets.name(2))))))
        state.selectedPart.value = LEG_A
        setContent(inspector())
        assertEquals(listOf(LEG_B), state.pairCandidates(LEG_A).map { it.id })
        onNodeWithTag(ShellTags.PAIR_WITH).performScrollTo().performClick()
        waitForIdle()
        val before = steps()
        onNodeWithTag(ShellTags.pairOption(LEG_B.value)).performClick()
        waitForIdle()
        assertEquals(before + 1, steps(), "pairing is one step")
        val character = session.document.value.character
        assertEquals(character.part(LEG_A)?.pair, assertNotNull(character.part(LEG_B)?.pair))
        assertEquals(SocketRef(body, HandSockets.name(1)), character.part(LEG_B)?.socket, "the right side sits on the left's socket")
        assertEquals(listOf(1), HandSockets.ids(params(body)), "the socket it left goes")
        onNodeWithTag(ShellTags.PAIR_WITH).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Unpair").performClick()
        waitForIdle()
        assertNull(session.document.value.character.part(LEG_B)?.pair)
    }

    @Test
    fun saveAsTemplateNamesItInADialogAndWritesTheFile() = runComposeUiTest {
        state.addJoint(body, 0.5f)
        setContent(inspector())
        onNodeWithTag(ShellTags.SAVE_TEMPLATE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.SAVE_NAME).performTextReplacement("Long body")
        onNodeWithTag(ShellTags.SAVE_OK).performClick()
        waitForIdle()
        assertTrue(saved.resolve("long-body.part.json").exists())
        onNodeWithTag(ShellTags.SAVE_NAME).assertDoesNotExist()
    }

    @Test
    fun aSavedTemplateIsOfferedAtOnceByTheTemplateMenuAndAddPart() = runComposeUiTest {
        val tube = assertNotNull(shipped.template(TemplateId("tube"))).ref
        session.send(HandSockets.add(body, 1, 0.3f, 0.35f, 0f, paired = false))
        session.send(HandSockets.add(body, 2, 0.3f, 0.65f, 0f, paired = false))
        session.send(AddParts(listOf(PartInstance(LEG_A, tube, SocketRef(body, HandSockets.name(1))))))
        state.selectedPart.value = LEG_A
        setContent {
            inspector()()
            StretchTheme(StretchThemes.Ink) { AddPartPopover(state) }
        }
        onNodeWithTag(ShellTags.SAVE_TEMPLATE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.SAVE_NAME).performTextReplacement("Long leg")
        onNodeWithTag(ShellTags.SAVE_OK).performClick()
        waitForIdle()
        val longLeg = TemplateId("long-leg")
        assertNotNull(state.templates.template(longLeg), "the catalog holds it without a restart")

        onNodeWithTag(ShellTags.TEMPLATE_MENU).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Long leg").performScrollTo().performClick()
        waitForIdle()
        assertEquals(longLeg, session.document.value.character.part(LEG_A)?.template?.id, "the Template menu offers it")

        state.offerSocket(state.openSocketsNow().first { it.name == HandSockets.name(2) })
        waitForIdle()
        onNodeWithTag(ShellTags.addTile(longLeg.value)).performClick()
        waitForIdle()
        state.addPart()
        waitForIdle()
        val added = session.document.value.character.parts.last()
        assertEquals(longLeg, added.template.id, "Add part offers it")
        work.settle(session)
        assertEquals(longLeg, session.builtTemplate(added).id, "the open character builds with it")
    }
}
