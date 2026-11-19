package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.AxisSpiral
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val HORN = PartId("horn")
private val LUMP = PartId("lump")

/** Shape's Curve section: places added, edited and removed as one command each, a spiral in their stead, and a warning where the curve is ignored. */
@OptIn(ExperimentalTestApi::class)
class CurveSectionTest {
    private val work = TestWork()

    private val state = AppState(shipped, work).also { state ->
        val new = newCharacter(blankPlan, shipped, "Ram") { "body" }
        val body = PartId("body")
        val horn = PartInstance(HORN, TemplateRef(TemplateId("horn"), 1), SocketRef(body, HandSockets.name(1)))
        val lump = PartInstance(LUMP, TemplateRef(TemplateId("blob"), 1), SocketRef(body, HandSockets.name(2)))
        val commands = new.commands + HandSockets.add(body, 1, 0.5f, 0f, 0f, paired = false) + HandSockets.add(body, 2, 0.5f, 0.5f, 0f, paired = false) +
            AddParts(listOf(horn, lump))
        state.open(commands.fold(new.document) { document, command -> command.apply(document).document })
    }
    private val session get() = checkNotNull(state.session.value)
    private fun params(part: PartId) = session.document.value.character.let { it.resolved(checkNotNull(it.part(part))).params }


    @Test
    fun placesAreAddedEditedAndRemovedOneCommandEach() = runComposeUiTest {
        state.selectedPart.value = HORN
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.CURVE).assertExists()
        onNodeWithTag(ShellTags.CURVE_ADD).performClick()
        waitForIdle()
        assertEquals(listOf(Vec3(0.5f, 0f, 0f), Vec3(1f, 0f, 0f)), AxisCurveParams.places(params(HORN)))
        onNodeWithTag(ShellTags.CURVE_REMOVE).assertIsNotEnabled()
        onNodeWithTag(ShellTags.CURVE_ADD).performClick()
        waitForIdle()
        assertEquals(3, AxisCurveParams.places(params(HORN)).size)
        onNodeWithTag(ShellTags.curvePlace(1, "up")).performClick()
        onNodeWithTag(ShellTags.curvePlace(1, "up")).performTextReplacement("0.3")
        onNodeWithTag(ShellTags.curvePlace(1, "up")).performImeAction()
        waitForIdle()
        assertEquals(0.3f, AxisCurveParams.places(params(HORN))[1].y, 1e-4f)
        onNodeWithTag(ShellTags.CURVE_REMOVE).performClick()
        waitForIdle()
        assertEquals(2, AxisCurveParams.places(params(HORN)).size)
        session.history.undo()
        assertEquals(3, AxisCurveParams.places(params(HORN)).size, "removing is one undo step")
    }

    @Test
    fun aSpiralHidesThePlaces() = runComposeUiTest {
        session.send(SetParameters(HORN, AxisCurveParams.of(AxisSpiral(1.5f, 0.2f, 0.5f))))
        state.selectedPart.value = HORN
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.parameter(AxisCurveParams.SPIRAL_PITCH)).assertExists()
        onNodeWithTag(ShellTags.CURVE_ADD).assertDoesNotExist()
        onNodeWithTag(ShellTags.curvePlace(0, "along")).assertDoesNotExist()
    }

    @Test
    fun aCurveOnAPartThatDoesNotSweepIsFlagged() = runComposeUiTest {
        session.send(SetParameters(LUMP, AxisCurveParams.of(listOf(Vec3(0.3f, 0.1f, 0f), Vec3(0.6f, 0.2f, 0f)))))
        state.selectedPart.value = LUMP
        setContent { StretchTheme(StretchThemes.Ink) { Column { ShapeInspector(state) { _, _, _, _, _ -> } } } }
        onNodeWithTag(ShellTags.CURVE_WARNING).assertExists()
        onNodeWithTag(ShellTags.CURVE_ADD).assertDoesNotExist()
    }
}

/** The Curve section's edits as plain functions. */
class CurveEditsTest {
    @Test
    fun anAddedPlaceGoesOnPastTheLastByHalfItsStep() {
        val places = listOf(Vec3(0.3f, 0f, 0f), Vec3(0.6f, 0.2f, 0f))
        assertEquals(Vec3(0.75f, 0.3f, 0f), places.withPlaceAdded().last())
    }

    @Test
    fun editingAPlaceReplacesTheWholeCurveAndTheSpiral() {
        val params = AxisCurveParams.of(AxisSpiral(1f)) + AxisCurveParams.of(listOf(Vec3(0.3f, 0f, 0f), Vec3(0.6f, 0f, 0f), Vec3(0.9f, 0f, 0f)))
        val command = curveCommand(HORN, params, listOf(Vec3(0.3f, 0f, 0f), Vec3(0.6f, 0.1f, 0f)), thisSide = false)
        assertNull(command.values[AxisCurveParams.SPIRAL_TURNS])
        assertNull(command.values[AxisCurveParams.along(3)], "a dropped place is cleared")
        assertEquals(0.1f, command.values[AxisCurveParams.up(2)])
    }
}
