package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.rig.LIMB_TOLERANCE
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.curveBones
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.surface.pose.BodyCurveTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 60_000L
private const val WIDTH = 2880
private const val HEIGHT = 1800

/** A stroke across a pane from just inside its left edge to just inside its right, off the straight line in its middle by a share of its height. */
private const val STROKE_FROM = 0.06f
private const val STROKE_TO = 0.94f
/** How far the Side and Top panes zoom out so the whole chain shows, past the torso they fit. */
private const val ZOOMED_OUT = 0.5f

private const val PROBE_FROM = 0.3f
private const val PROBE_TO = 0.7f

/** How far the Side stroke sinks the head's end below its rest, in body units, and how many points it takes between joints. */
private const val SINK = 0.03f
private const val DENSE = 4
private const val TOP_SWAY = 0.06f
private const val STROKE_STEPS = 40

private object NoCurveFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Body curve tool in the app whole, over the saved fennec standing on four planted paws: Side, then Top, Length, Clear curve. */
@OptIn(ExperimentalTestApi::class)
class BodyCurveFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val paws = listOf("front-paw-l", "front-paw-r", "back-paw-l", "back-paw-r").map(::PartId)

    @AfterTest
    fun stop() = scope.cancel()

    private fun ComposeUiTest.fennecOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoCurveFiles) }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true }
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** Draws a stroke across [pane] from left to right, its middle off the straight line by [sag] of the pane's height. */
    private fun ComposeUiTest.stroke(pane: SheetPane, sag: Float) {
        onNodeWithTag(sheetPaneTag(pane)).performMouseInput {
            val points = List(STROKE_STEPS + 1) { i ->
                val t = i / STROKE_STEPS.toFloat()
                Offset(width * (STROKE_FROM + (STROKE_TO - STROKE_FROM) * t), height * (0.5f + sag * sin(PI * t).toFloat()))
            }
            moveTo(points.first())
            press()
            points.drop(1).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
    }

    /** Draws a stroke across [pane] through [points], in the pane's pixels. */
    private fun ComposeUiTest.strokeThrough(pane: SheetPane, points: List<Offset>) {
        onNodeWithTag(sheetPaneTag(pane)).performMouseInput {
            moveTo(points.first())
            press()
            points.drop(1).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
    }

    /**
     * The Side pane's pixels for body x and y, read from a straight probe stroke across it, which is then undone: the pane looks
     * straight along the body's side, so the map is a scale and a shift, x either way round.
     */
    private fun ComposeUiTest.sidePixels(state: AppState): (Vec3) -> Offset {
        val session = assertNotNull(state.session.value)
        val size = onNodeWithTag(sheetPaneTag(SheetPane.SIDE)).fetchSemanticsNode().size
        val from = Offset(size.width * PROBE_FROM, size.height / 2f)
        val to = Offset(size.width * PROBE_TO, size.height / 2f)
        strokeThrough(SheetPane.SIDE, List(STROKE_STEPS + 1) { from + (to - from) * (it / STROKE_STEPS.toFloat()) })
        val probe = assertNotNull(session.document.value.character.pose.curve?.stroke(View.SIDE)).points
        state.undo()
        waitForIdle()
        val perPixel = (probe.last().x - probe.first().x) / (to.x - from.x)
        val scale = abs(perPixel)
        return { p -> Offset(from.x + (p.x - probe.first().x) / perPixel, from.y - (p.y - probe.first().y) / scale) }
    }

    /**
     * The chain at rest from the tail's tip through its joints to the head's end, as the body curve lays it: the tail's joints, the
     * torso's past its first, which the tail hangs beside, and the head's; sinking more toward the head, which ends [SINK] body units low, as a bow does.
     */
    private fun restChainSunk(rig: Rig): List<Vec3> {
        val bones = assertNotNull(curveBones(rig))
        val tip = assertNotNull(rig.bone(bones.behind.last())).let { it.pivot + it.direction * it.length }
        val end = assertNotNull(rig.bone(bones.ahead.last())).let { it.pivot + it.direction * it.length }
        val joints = listOf(tip) + (bones.behind.reversed() + bones.spine.drop(1) + bones.ahead).mapNotNull { rig.bone(it)?.pivot } + end
        val dense = joints.zipWithNext().flatMap { (a, b) -> List(DENSE) { a + (b - a) * (it / DENSE.toFloat()) } } + joints.last()
        return dense.mapIndexed { i, p -> p - Vec3.Y * (SINK * (i.toFloat() / dense.lastIndex).let { it * it }) }
    }

    private fun ComposeUiTest.statusEnd(text: String) =
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains(text, substring = true)

    @Test
    fun aStrokeInSideThenTopBendsTheBodyOverItsPlantedPaws() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecOpen()
        val session = assertNotNull(state.session.value)
        paws.forEach { session.send(SetPlanted(it, true)) }
        key(Key.Six)
        key(Key.C)
        assertEquals(BuildTool.BODY_CURVE, state.tool.value)
        onNodeWithTag(ShellTags.tool(BuildTool.BODY_CURVE)).assertExists()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.rigNow() != null }
        waitForIdle()
        onNodeWithTag(ShellTags.STATUS_STAGE).onChildren().onFirst().assertTextContains("Pose · Body curve", substring = true)
        statusEnd("4 paws planted")
        val rig = assertNotNull(state.rigNow())
        val solver = PoseSolver(rig)
        val bones = assertNotNull(curveBones(rig))
        fun pose() = session.document.value.character.pose
        fun ends(pose: Pose) = solver.chains.limbs.associate { it.id to solver.endOf(pose, it) }
        val standing = ends(pose())

        for (view in listOf(View.SIDE, View.TOP)) state.paneNavigation.update(view) { it.copy(zoom = ZOOMED_OUT) }
        waitForIdle()
        val pixels = sidePixels(state)
        val steps = session.history.steps.value.done.size
        strokeThrough(SheetPane.SIDE, restChainSunk(rig).map(pixels))
        assertEquals(steps + 1, session.history.steps.value.done.size, "a stroke is one step")
        assertIs<SetPose>(session.history.steps.value.done.last())
        val side = pose()
        assertNotNull(side.curve?.stroke(View.SIDE), "the stroke is the Side stroke")
        val bent = bones.spine.maxOf { abs(side.of(it.part, it.joint)?.degrees ?: 0f) }
        println("the Side stroke bends a torso joint by $bent degrees")
        assertTrue(bent > 1f, "the torso bends")
        val held = ends(side).mapValues { (id, at) -> (at - standing.getValue(id)).length }
        println("planted paws move ${held.values.max()} at most")
        for ((id, d) in held) assertTrue(d <= LIMB_TOLERANCE, "${id.value} moved $d")
        statusEnd("Length 100% · 4 paws planted")

        stroke(SheetPane.TOP, TOP_SWAY)
        assertEquals(steps + 2, session.history.steps.value.done.size)
        val top = pose()
        assertNotNull(top.curve?.stroke(View.TOP))
        assertEquals(side.curve?.stroke(View.SIDE), top.curve?.stroke(View.SIDE), "the Side stroke stays")
        val leant = (bones.spine + bones.behind).maxOf { abs(top.of(it.part, it.joint)?.lean ?: 0f) }
        println("the Top stroke leans a chain bone by $leant degrees")
        assertTrue(leant > 1f, "the chain leans sideways")
        for (bone in bones.spine + bones.behind) {
            assertEquals(side.of(bone.part, bone.joint)?.degrees ?: 0f, top.of(bone.part, bone.joint)?.degrees ?: 0f, 1e-3f, "$bone keeps its Side bend")
        }

        onNodeWithTag(BodyCurveTags.LENGTH).performMouseInput {
            moveTo(Offset(width * 0.5f, height / 2f))
            press()
            moveTo(Offset(width * 0.4f, height / 2f))
            moveTo(Offset(width * 0.3f, height / 2f))
            release()
        }
        waitForIdle()
        assertEquals(steps + 3, session.history.steps.value.done.size, "a Length drag is one step")
        val length = assertNotNull(pose().curve).length
        assertTrue(abs(length - 1f) > 0.01f, "the drag changes the curve's length: $length")
        statusEnd("Length ${(length * 100f).roundToInt()}%")

        onNodeWithTag(BodyCurveTags.CLEAR).performScrollTo().performClick()
        waitForIdle()
        assertEquals(steps + 4, session.history.steps.value.done.size)
        assertNull(pose().curve)
        val chain = (bones.behind + bones.spine + bones.ahead).map { it.part to it.joint }.toSet()
        assertTrue(pose().bones.none { (it.part to it.joint) in chain && !it.isRest }, "Clear curve rests the chain")
        statusEnd("4 paws planted")
        repeat(4) { state.undo() }
        assertEquals(steps, session.history.steps.value.done.size)
        assertNull(pose().curve)
    }
}
