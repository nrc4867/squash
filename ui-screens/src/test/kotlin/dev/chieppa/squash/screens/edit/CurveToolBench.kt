package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.StrokeLook
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal const val PANE_TAG = "pane"
internal const val PANE_WIDTH = 600
internal const val PANE_HEIGHT = 400
internal const val SCALE = 100f

internal val TEST_CURVE_LOOK = CurveLook(
    ring = Color.Yellow,
    fill = Color.White,
    affected = Color.Blue,
    handleWidth = 2f,
    handleRadius = 4f,
    anchorHalf = 5f,
    anchorEdge = 2f,
    affectedWidth = 5f,
    affectedDash = 8f,
    affectedGap = 6f,
    grabReach = 12f,
)

internal val TEST_STROKE_LOOK = StrokeLook(
    ink = Color.Black,
    width = 7f,
    tip = Color.Yellow,
    tipRadius = 8f,
    tipRing = 3f,
    connector = Color.Magenta,
    connectorWidth = 3f,
    connectorDash = 10f,
    connectorGap = 8f,
    dropped = Color.Cyan,
    droppedWidth = 3f,
    droppedDash = 4f,
    droppedGap = 6f,
    startFill = Color.White,
    startRadius = 7f,
    startLitRadius = 13f,
    startRing = 3f,
)

/** A straight-edged outline through [corners], one section per edge. */
internal fun edges(vararg corners: Pair<Float, Float>): OutlineCurve {
    val points = mutableListOf(CurvePoint(corners[0].first, corners[0].second))
    for (i in 1 until corners.size) {
        val (x0, y0) = corners[i - 1]
        val (x1, y1) = corners[i]
        points += CurvePoint(x0 + (x1 - x0) / 3f, y0 + (y1 - y0) / 3f)
        points += CurvePoint(x0 + (x1 - x0) * 2f / 3f, y0 + (y1 - y0) * 2f / 3f)
        points += CurvePoint(x1, y1)
    }
    return OutlineCurve(points)
}

/** Out from the socket plane along x and back, on the plane at both ends. */
internal val LEG_SIDE = edges(0f to 0.5f, 0.5f to 0.5f, 1f to 0.5f, 1.5f to 0.5f, 2f to 0f, 1.5f to -0.5f, 0f to -0.5f)

/** A leg in a session on its own, with [side] as its Side outline, and a pane to work in. */
internal class CurveToolBench(side: OutlineCurve? = LEG_SIDE) {
    val id = PartId("p")
    val state: AppState
    val session: CharacterSession

    init {
        val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        state = AppState(folder)
        session = CharacterSession(SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1))), TemplateLibrary(folder.parts), scope)
        session.send(AddPart(PartInstance(id, folder.parts.first { it.id.value == "leg" }.ref)))
        if (side != null) session.send(SetOutline(id, View.SIDE, side))
        state.session.value = session
        state.selectedPart.value = id
    }

    fun camera(view: View = View.SIDE) = LockedCamera(view, PartAxes.of(Vec3.X), SheetFit(Vec3(1f, 0f, 0f), SCALE), PANE_WIDTH, PANE_HEIGHT)

    fun outline(view: View = View.SIDE): OutlineCurve? = session.document.value.character.resolved(id)?.outlines?.get(view)

    fun steps() = session.history.steps.value.done.size

    /** Where point [index] of the Side outline shows. */
    fun pixelOf(index: Int): Offset = camera().toPixel(checkNotNull(outline()).points[index])
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showPane(tool: PaneTool, camera: LockedCamera) {
    setStretchContent {
        with(density) {
            Box(Modifier.size(PANE_WIDTH.toDp(), PANE_HEIGHT.toDp())) {
                PaneViewport(SheetPane.SIDE, null, tool.pointerInput(camera.view, camera).testTag(PANE_TAG))
            }
        }
    }
}

/** A press at the first of [path], moves through the rest, and a release. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.mouseDrag(path: List<Offset>) {
    onNodeWithTag(PANE_TAG).performMouseInput {
        moveTo(path.first())
        press()
        path.drop(1).forEach { moveTo(it) }
        release()
    }
    waitForIdle()
}
