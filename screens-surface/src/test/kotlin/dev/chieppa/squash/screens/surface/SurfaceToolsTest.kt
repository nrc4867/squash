package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.SetLineRules
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.screens.fennec.BACK_WEIGHT
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXELS = 800
private const val PIXEL_CLOSE = 1.5f
private const val DENSITY = 2f

/** The surface tools' logic over the saved fennec's real graph, through a free camera and a locked one. */
class SurfaceToolsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val folder = TemplateFolder.shipped()

    @AfterTest
    fun stop() = scope.cancel()

    private fun session(): CharacterSession {
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(folder.parts, folder.plans), scope, Dispatchers.Default)
        fennecCommands().forEach(session::send)
        settle(session)
        return session
    }

    private fun settle(session: CharacterSession) = runBlocking { session.graph.settled.first { it } }

    private fun free(session: CharacterSession, from: Vec3): FullView {
        val box = assertNotNull(assertNotNull(session.assembly.output.value).box)
        val camera = CameraView(eye = (box.center + from.normalized() * (box.radius * 3f)).toGpu(), target = box.center.toGpu())
        return FullView({ camera }, PIXELS, PIXELS)
    }

    private fun locked(session: CharacterSession): LockedCamera {
        val character = assertNotNull(session.assembly.output.value)
        val box = assertNotNull(character.box)
        val axes = PartAxes.of(Vec3.X, false, null)
        return LockedCamera(View.SIDE, axes, SheetFit(box.center, PIXELS / (box.radius * 2.2f), box.radius), PIXELS, PIXELS)
    }

    @Test
    fun aPickedAnchorResolvesToWhereItWasPickedThroughEitherCamera() {
        val session = session()
        val character = assertNotNull(session.assembly.output.value)
        val center = assertNotNull(character.part(TORSO)?.box).center
        for (view in listOf(free(session, Vec3(0.2f, 0.4f, -1f)), locked(session))) {
            val pixel = assertNotNull(view.toPixel(center))
            val hit = assertNotNull(surfaceHit(character, pixel, view))
            val placed = assertNotNull(character.part(hit.part))
            val resolved = assertNotNull(placed.surface.patches.first { it.name == hit.patch }).let { patch ->
                placed.origin + placed.surface.shown(patch.positionAt(hit.u, hit.v))
            }
            val shown = assertNotNull(view.toPixel(resolved))
            assertTrue((shown - pixel).getDistance() < PIXEL_CLOSE * 2f, "the anchor shows at $shown for $pixel")
        }
    }

    @Test
    fun aLockedCameraPicksTheNearestPartAnyPartBeingOnShow() {
        val session = session()
        val character = assertNotNull(session.assembly.output.value)
        val view = locked(session)
        val head = assertNotNull(character.part(HEAD)?.box).center
        assertEquals(HEAD, surfaceHit(character, view.toPixel(head), view)?.part)
    }

    @Test
    fun aStrokeStoresThroughOneCommandAndOneUndoTakesItBack() {
        val session = session()
        val view = free(session, Vec3(0.2f, 0.4f, -1f))
        val character = assertNotNull(session.assembly.output.value)
        val start = assertNotNull(view.toPixel(assertNotNull(character.part(TORSO)?.box).center))
        val anchors = List(3) { assertNotNull(surfaceHit(character, start + Offset(it * 8f, 0f), view)).anchor }
        val before = session.document.value
        val command = assertNotNull(strokeCommand(before.character, anchors, thisSide = false))
        session.send(command)
        val mark = session.document.value.character.resolved(anchors.first().part)?.surface?.marks?.first { it.id == command.id }
        assertEquals(anchors, mark?.points)
        session.history.undo()
        assertEquals(before, session.document.value)
        assertNull(strokeCommand(before.character, anchors.take(1), thisSide = false), "one anchor is no stroke")
    }

    @Test
    fun freeIdsAvoidWhatTheCharacterHolds() {
        val session = session()
        val character = session.document.value.character
        val first = freeMarkId(character)
        val anchors = assertNotNull(character.resolved(HEAD)).surface.marks.first().points
        session.send(assertNotNull(strokeCommand(character, anchors, thisSide = false)))
        assertNotEquals(first, freeMarkId(session.document.value.character))
        assertNotEquals(BACK_WEIGHT, freeWeightId(character))
    }

    @Test
    fun aRingCoversTheSameScreenSizeWhateverTheCamera() {
        val session = session()
        val character = assertNotNull(session.assembly.output.value)
        val center = assertNotNull(character.part(TORSO)?.box).center
        val view = free(session, Vec3(0.2f, 0.4f, -1f))
        val hit = assertNotNull(surfaceHit(character, assertNotNull(view.toPixel(center)), view))
        val radius = assertNotNull(weightRadius(character, hit, view, 24f))
        val length = assertNotNull(character.part(hit.part)).surface.axisLength
        val pixels = radius * length * view.pixelsPerUnitAt(hit.position)
        assertTrue(abs(pixels - 12f) < 0.01f, "half the ring is $pixels pixels")
    }

    @Test
    fun theHeavierStretchStaysHeavierWhereverItTurnsToTheSilhouette() {
        val session = session()
        val lines = assertNotNull(session.bodyLines.output.value)
        val torso = assertNotNull(lines.parts[TORSO])
        val plain = assertNotNull(lines.parts[HEAD])
        for (degrees in listOf(70f, 90f, 110f, 250f, 270f, 290f)) {
            val radians = Math.toRadians(degrees.toDouble())
            val toViewer = Vec3(kotlin.math.cos(radians).toFloat(), 0.05f, -kotlin.math.sin(radians).toFloat()).normalized()
            val heaviest = torso.grids.flatMap { it.contours { toViewer } }.flatMap { it.points }.maxOf { it.weight }
            assertTrue(heaviest > 1.5f, "at $degrees degrees the back's line weighs $heaviest")
            val plainest = plain.grids.flatMap { it.contours { toViewer } }.flatMap { it.points }.maxOf { it.weight }
            assertEquals(1f, plainest, "a part with no weight on it stays at the rule")
        }
    }

    @Test
    fun aSurfaceEditReweighsTheLinesAndUndoRestoresThem() {
        val session = session()
        val before = assertNotNull(session.bodyLines.output.value)
        val creases = before.parts.getValue(TORSO).crease
        session.send(SetLineWeight(TORSO, BACK_WEIGHT, null))
        settle(session)
        val without = assertNotNull(session.bodyLines.output.value)
        val toViewer = Vec3(0f, 0.05f, -1f).normalized()
        assertEquals(1f, without.parts.getValue(TORSO).grids.flatMap { it.contours { toViewer } }.flatMap { it.points }.maxOf { it.weight })
        assertEquals(creases.size, without.parts.getValue(TORSO).crease.size)
        session.history.undo()
        settle(session)
        assertTrue(assertNotNull(session.bodyLines.output.value).parts.getValue(TORSO).grids.flatMap { it.contours { toViewer } }.flatMap { it.points }.maxOf { it.weight } > 1.5f)
    }

    @Test
    fun theLookFollowsTheRulesAtTheScreensDensity() {
        val look = characterLook(LineRules(silhouette = 5f, interior = 1.5f), DENSITY)
        assertEquals(10f, look.lines.silhouettePixels)
        assertEquals(3f, look.lines.interiorPixels)
        assertEquals(3f, look.creases.interiorPixels)
        val session = session()
        session.send(SetLineRules(LineRules(silhouette = 6f)))
        assertEquals(6f, session.document.value.character.lineRules.silhouette)
        session.history.undo()
        assertEquals(LineRules(), session.document.value.character.lineRules)
    }

    @Test
    fun theFiguresThickLineShowsOnlyClearOfItAndTheSelectionsOverGhosts() {
        val session = session()
        val lines = assertNotNull(session.bodyLines.output.value)
        val work = assertNotNull(session.bodySurfaceWork.output.value)
        val look = characterLook(LineRules(), 1f)
        val ink = androidx.compose.ui.graphics.Color.Black
        val surface = SurfaceLines(lines, work, look, LineRules().taper, ink, 1f)
        val view = free(session, Vec3(0.6f, 0.3f, -1f))
        val whole = surface.figure(view)
        assertTrue(whole.any { it.layer == DepthLayer.BACK && it.rule.interiorPixels == 6f }, "the thick line shows 4 pixels across with the thin one's inner half")
        assertTrue(whole.any { it.layer == DepthLayer.SCENE && it.rule.interiorPixels == 2f })
        val selected = surface.selected(TORSO, locked(session))
        assertTrue(selected.any { it.layer == DepthLayer.BEHIND_FRONT })
        assertTrue(selected.all { it.layer == DepthLayer.BEHIND_FRONT || it.layer == DepthLayer.FRONT })
        assertEquals(whole.size, surface.figure(view).size)
        assertTrue(whole === surface.figure(view), "a camera that holds still costs nothing")
    }

    @Test
    fun anInkedBorderDrawsAtTheInteriorWeightInEveryWayThePanesAsk() {
        val session = session()
        val lines = assertNotNull(session.bodyLines.output.value)
        val character = session.document.value.character
        val holder = TAIL
        val region = assertNotNull(character.resolved(holder)).surface.regions.first()
        fun drawn(ink: Boolean): SurfaceLines {
            session.send(SetRegion(holder, region.id, region.copy(ink = ink)))
            settle(session)
            return SurfaceLines(lines, assertNotNull(session.bodySurfaceWork.output.value), characterLook(LineRules(), 1f), LineRules().taper, androidx.compose.ui.graphics.Color.Black, 1f)
        }
        val view = locked(session)
        val without = drawn(false).figure(view)
        val with = drawn(true).figure(view)
        assertEquals(1, with.count { it.rule.interiorPixels == 2f } - without.count { it.rule.interiorPixels == 2f }, "the interior weight, as creases and strokes draw")
        val selected = drawn(true).selected(holder, view)
        assertTrue(selected.last().layer == DepthLayer.FRONT, "with the selection, over what it covers")
    }

    @Test
    fun eraseTakesWhatItCrossesOnce() {
        val session = session()
        val work = assertNotNull(session.bodySurfaceWork.output.value)
        val view = free(session, Vec3(1f, 0.1f, 0f))
        val mark = assertNotNull(work.parts[HEAD]).looseMarks().first { !it.id.value.startsWith("fur-") }
        val facing = mark.points.first()
        val at = assertNotNull(view.toPixel(facing.position))
        val seen = HashSet<String>()
        val taken = erasedAt(work, view, at, 8f, { false }, seen)
        val command = assertIs<SetMark>(taken.first { it is SetMark })
        assertEquals(mark.id, command.id)
        assertNull(command.mark)
        assertEquals(emptyList(), erasedAt(work, view, at, 8f, { false }, seen).filterIsInstance<SetMark>().filter { it.id == mark.id }, "a drag takes each once")
    }

    @Test
    fun amountLeavesAWeightAnUndoHasTakenAlone() {
        val session = session()
        val picked = PickedWeight(TORSO, BACK_WEIGHT)
        val command = assertNotNull(amountCommand(session.document.value.character, picked, 0.3f, thisSide = false))
        session.send(command)
        assertEquals(0.3f, session.document.value.character.resolved(TORSO)?.surface?.lineWeights?.single()?.amount)
        session.send(SetLineWeight(TORSO, BACK_WEIGHT, null))
        assertNull(amountCommand(session.document.value.character, picked, 0.4f, thisSide = false))
    }

    @Test
    fun theDrawerListsLooseMarksByPartWithAPairOnce() {
        val rows = markRows(session().document.value.character, folder, TemplateLibrary(folder.parts, folder.plans))
        assertEquals(listOf(BACK_WEIGHT), rows.weights.map { it.id })
        assertEquals(0.6f, rows.weights.single().amount)
        assertEquals("Saddle", rows.weights.single().name, "a weight's row names the area it lies on")
        assertEquals(listOf(HEAD, HEAD), rows.strokes.map { it.part }, "the mouth's two lines; the eyes are groups")
        assertTrue(rows.fur.all { it.count > 0 })
        assertEquals(listOf(TORSO, TAIL), rows.fur.map { it.part })
    }
}
