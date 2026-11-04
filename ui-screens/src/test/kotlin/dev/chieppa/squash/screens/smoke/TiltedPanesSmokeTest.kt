package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.sheet.OutlineLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.GpuSnapshot
import dev.chieppa.squash.render.ViewsScene
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val PANES_WIDTH = 1400
private const val PANES_HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L
private const val RUN_POINTS = 24

/** How far the filled part may sit from its outline on screen, in pixels: the rim the shader draws, and rings a span apart. */
private const val FILL_SLACK = 10f

private val OUTLINE_LOOK = OutlineLook(Color(0xFF3C3532), 4f, Color(0xFFD9772B), 4f, 8f)

/** An outline from the socket plane out to [length] along x and back, [half] wide. */
private fun openBlade(length: Float, half: Float): OutlineCurve = edges(
    *Array(RUN_POINTS + 1) {
        val angle = PI * it / RUN_POINTS
        length * sin(angle).toFloat() to half * cos(angle).toFloat()
    },
)

private fun closedEllipse(x: Float, y: Float): OutlineCurve = edges(
    *Array(RUN_POINTS + 1) {
        val angle = 2.0 * PI * it / RUN_POINTS
        x * cos(angle).toFloat() to y * sin(angle).toFloat()
    },
)

/** The panes rendered offscreen for an ear and a tail, undrawn and drawn, left in the smoke images for a person to read. */
@Tag("slow")
class TiltedPanesSmokeTest {
    private val templates = TemplateFolder.shipped()
    private fun ref(id: String) = templates.parts.first { it.id == TemplateId(id) }.ref
    private val torso = PartInstance(PartId("torso"), ref("torso"))
    private val tail = PartInstance(PartId("tail"), ref("tail"), SocketRef(torso.id, "tail"))
    private val head = PartInstance(PartId("head"), ref("head"), SocketRef(torso.id, "neck"))
    private val leftEar = PartInstance(PartId("left-ear"), ref("ear"), SocketRef(head.id, "ear"), pair = PairId("ears"), side = Side.LEFT)
    private val rightEar = PartInstance(PartId("right-ear"), ref("ear"), SocketRef(head.id, "ear"), pair = PairId("ears"), side = Side.RIGHT)

    private fun session(scope: CoroutineScope): CharacterSession {
        val plan = templates.plans.first()
        val character = Character(CharacterId("dog"), "Dog", BodyPlanRef(plan.id, plan.version))
        return CharacterSession(SquashDocument(character), TemplateLibrary(templates.parts, templates.plans), scope).also {
            it.send(AddPart(torso))
            it.send(AddPart(tail))
            it.send(AddPart(head))
            it.send(AddParts(listOf(leftEar, rightEar)))
        }
    }

    private fun selection(session: CharacterSession, id: PartId): SheetSelection = runBlocking {
        withTimeout(SETTLE_MILLIS) {
            session.graph.settled.first { it }
            val assembled = session.assembly.output.first { it != null }!!
            val document = session.document.value.character
            assertNotNull(sheetSelection(assembled, id, document.resolved(assertNotNull(document.part(id))).outlines))
        }
    }

    private val rects = sheetPaneRects(PANES_WIDTH, PANES_HEIGHT, 2)

    private fun render(selection: SheetSelection, name: String): dev.chieppa.stretch.gpu.GpuImage {
        val errors = CopyOnWriteArrayList<String>()
        val input = panesInput(selection, SMOKE_LOOK, rects, PANES_WIDTH, PANES_HEIGHT, outlines = OUTLINE_LOOK)
        val image = GpuSnapshot.render(sheetFrame(input), PANES_WIDTH, PANES_HEIGHT, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, name)
        return image
    }

    /** The part's fill in [view]'s pane lies inside and reaches the drawn outline's ends along the plane's x. */
    private fun assertFillOnOutline(selection: SheetSelection, image: dev.chieppa.stretch.gpu.GpuImage, view: View, label: String) {
        val input = panesInput(selection, SMOKE_LOOK, rects, PANES_WIDTH, PANES_HEIGHT)
        val camera = assertNotNull(input.cameras[view])
        val body = selection.part.axes.bodyViewOf(view)
        val pane = rects[SheetPane.entries.first { it.body == body }.ordinal]
        val fill = if (selection.part.mirrored) SMOKE_LOOK.farPart else SMOKE_LOOK.part
        val filled = image.pointsIn(pane, fill.base)
        assertTrue(filled.isNotEmpty(), "$label $view shows the part filled")
        val outline = assertNotNull(selection.outlines[view])
        val drawn = outline.points.map(camera::toPixel)
        for ((filledSpan, drawnSpan) in listOf(
            filled.minOf { it.x }..filled.maxOf { it.x } to (drawn.minOf { it.x }..drawn.maxOf { it.x }),
            filled.minOf { it.y }..filled.maxOf { it.y } to (drawn.minOf { it.y }..drawn.maxOf { it.y }),
        )) {
            assertTrue(abs(filledSpan.start - drawnSpan.start) < FILL_SLACK && abs(filledSpan.endInclusive - drawnSpan.endInclusive) < FILL_SLACK, "$label $view: fill $filledSpan under outline $drawnSpan")
        }
    }

    private fun tiltedPanes(id: PartId, name: String, side: OutlineCurve, top: OutlineCurve, front: OutlineCurve) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = session(scope)
        render(selection(session, id), "$name-undrawn")
        session.send(SetOutline(id, View.SIDE, side))
        session.send(SetOutline(id, View.TOP, top))
        session.send(SetOutline(id, View.FRONT, front))
        val drawn = selection(session, id)
        val image = render(drawn, "$name-drawn")
        assertFillOnOutline(drawn, image, View.SIDE, name)
        assertFillOnOutline(drawn, image, View.TOP, name)
        scope.cancel()
    }

    private val earSide = openBlade(0.32f, 0.07f)
    private val earTop = openBlade(0.32f, 0.09f)
    private val earFront = closedEllipse(0.09f, 0.07f)

    @Test
    fun aLeftEarsPanes() = tiltedPanes(leftEar.id, "ear-left", earSide, earTop, earFront)

    @Test
    fun aRightEarsPanes() = tiltedPanes(rightEar.id, "ear-right", earSide, earTop, earFront)

    @Test
    fun aTailsPanes() = tiltedPanes(tail.id, "tail", openBlade(0.55f, 0.06f), openBlade(0.55f, 0.08f), closedEllipse(0.08f, 0.06f))

    @Test
    fun aTailsDisagreementLinesLieAcrossItsAxis() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = session(scope)
        session.send(SetOutline(tail.id, View.SIDE, openBlade(0.55f, 0.06f)))
        session.send(SetOutline(tail.id, View.TOP, openBlade(0.4f, 0.08f)))
        render(selection(session, tail.id), "tail-disagree")
        scope.cancel()
    }
}
