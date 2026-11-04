package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.stretch.gpu.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val TORSO_ID = PartId("torso")
private val LEFT_ID = PartId("leg")
private val RIGHT_ID = PartId("leg-r")
private const val CROSSING_SAMPLES = 40
private const val CROSSING_TOLERANCE = 12
private val OVERSKETCHED = edges(0f to 0.2f, 0.5f to 0.16f, 0.9f to 0.07f, 0.9f to -0.1f, 0.5f to -0.2f, 0f to -0.2f)

/** Paint on a pair, across a socket, over a pole and on a pad, through the real graph and scene, left for a person to read. */
@Tag("slow")
class SurfaceCrossingSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pair = PairId("front")
    private val torso = PartInstance(TORSO_ID, seamsRef("torso"))
    private val left = PartInstance(LEFT_ID, seamsRef("leg"), SocketRef(TORSO_ID, "front-leg"), pair = pair, side = Side.LEFT)
    private val right = PartInstance(RIGHT_ID, seamsRef("leg"), SocketRef(TORSO_ID, "front-leg"), pair = pair, side = Side.RIGHT)

    private fun finish() {
        scope.cancel()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aPairMirrorsItsPaintAndFarSideDarkens() {
        val session = seamsSession(scope, listOf(torso, left, right))
        (seamsPalette() + seamsPaint(LEFT_ID) + SetBaseSlot(TORSO_ID, FUR)).forEach(session::send)
        session.settled()
        val character = assertNotNull(session.assembly.output.value)
        val parts = character.parts.filter { it.id != null }
        val box = parts.mapNotNull { it.box }.reduce { a, b -> a.union(b) }
        val near = assertNotNull(character.part(LEFT_ID)).origin.z
        val eye = box.center + Vec3(0.6f, 0.35f, if (near >= 0f) 1f else -1f).normalized() * box.radius * 2.6f
        session.showColorAt(pixelsPerUnit(box.radius * 2.6f))
        session.settled()
        val draws = parts.map { seamsDraw(session, it, ObjectShading(Shading.TWO_TONE), eye - box.center) }
        val camera = CameraView(eye = eye.toGpuVec(), target = box.center.toGpuVec())
        seamsRender("seams-pair-two-tone", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, draws))), errors)
        finish()
    }

    @Test
    fun aStrokeAndABorderCrossTheSocketOntoTheParent() {
        val session = seamsSession(scope, listOf(torso, left, right))
        (seamsPalette() + SetBaseSlot(LEFT_ID, FUR) + SetBaseSlot(TORSO_ID, FUR)).forEach(session::send)
        session.settled()
        val character = assertNotNull(session.assembly.output.value)
        val leg = assertNotNull(character.part(LEFT_ID))
        val body = assertNotNull(character.part(TORSO_ID))
        val toCamera = Vec3(0.55f, 0.25f, if (leg.origin.z >= 0f) 1f else -1f).normalized()
        val front = leg.vFacing(0.1f, toCamera)
        val patch = leg.surface.patches.first { it.slices != null }
        fun onBody(u: Float, v: Float) = leg.origin + leg.surface.shown(patch.surfaceAt(u, v).position)
        val (bodyU, bodyV) = body.nearest(onBody(0.04f, front))
        val stroke = listOf(Anchor(LEFT_ID, u = 0.28f, v = front), Anchor(LEFT_ID, u = 0.14f, v = front), Anchor(LEFT_ID, u = 0.04f, v = front)) +
            List(3) { Anchor(TORSO_ID, u = bodyU + 0.03f * it, v = bodyV - 0.015f * it) }
        val border = listOf(Anchor(LEFT_ID, u = 0.05f, v = front - 0.22f), Anchor(LEFT_ID, u = 0.2f, v = front - 0.12f), Anchor(LEFT_ID, u = 0.2f, v = front + 0.12f), Anchor(LEFT_ID, u = 0.05f, v = front + 0.22f)) +
            listOf(Anchor(TORSO_ID, u = bodyU - 0.09f, v = bodyV), Anchor(TORSO_ID, u = bodyU, v = bodyV - 0.1f), Anchor(TORSO_ID, u = bodyU + 0.09f, v = bodyV))
        session.send(SetRegion(LEFT_ID, RegionId("sock"), Region(RegionId("sock"), border, CREAM)))
        session.send(SetBrushStroke(LEFT_ID, BrushStrokeId("across"), BrushStroke(BrushStrokeId("across"), stroke, SPOT, 0.035f)))
        val distance = 1.3f
        session.showColorAt(pixelsPerUnit(distance))
        session.settled()
        val target = leg.origin + Vec3(0f, -0.1f, 0f)
        val camera = CameraView(eye = (target + toCamera * distance).toGpuVec(), target = target.toGpuVec())
        val draws = character.parts.filter { it.id != null }.map { seamsDraw(session, it, ObjectShading(), camera.toCameraFrom(target)) }
        val color = assertNotNull(session.bodyColor.output.value)
        assertTrue(color.parts.getValue(TORSO_ID).layers.isNotEmpty(), "the shapes arrive on the parent")
        seamsRender("seams-cross-socket", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, draws))), errors)
        finish()
    }

    /** Along a soft stroke's middle from leg to torso the image holds the stroke's color, with no dip where each run would fade its own end. */
    @Test
    fun aSoftStrokeHoldsItsStrengthAcrossTheSocket() {
        val session = seamsSession(scope, listOf(torso, left, right))
        (seamsPalette() + SetBaseSlot(LEFT_ID, FUR) + SetBaseSlot(TORSO_ID, FUR)).forEach(session::send)
        session.settled()
        val character = assertNotNull(session.assembly.output.value)
        val leg = assertNotNull(character.part(LEFT_ID))
        val body = assertNotNull(character.part(TORSO_ID))
        val toCamera = Vec3(0.55f, 0.25f, if (leg.origin.z >= 0f) 1f else -1f).normalized()
        val front = leg.vFacing(0.1f, toCamera)
        val patch = leg.surface.patches.first { it.slices != null }
        fun onLeg(u: Float) = leg.origin + leg.surface.shown(patch.surfaceAt(u, front).position)
        val rising = (onLeg(0.02f) - onLeg(0.2f)).normalized()
        val onTorso = List(4) { body.nearest(onLeg(0.02f) + rising * (0.05f + 0.03f * it)) }
        val anchors = listOf(0.4f, 0.3f, 0.2f, 0.1f).map { Anchor(LEFT_ID, u = it, v = front) } + onTorso.map { (u, v) -> Anchor(TORSO_ID, u = u, v = v) }
        session.send(SetBrushStroke(LEFT_ID, BrushStrokeId("soft"), BrushStroke(BrushStrokeId("soft"), anchors, SOCK, 0.12f, 0.8f)))
        val distance = 1.3f
        session.showColorAt(pixelsPerUnit(distance))
        session.settled()
        val target = leg.origin + Vec3(0f, -0.1f, 0f)
        val camera = CameraView(eye = (target + toCamera * distance).toGpuVec(), target = target.toGpuVec())
        val draws = character.parts.filter { it.id != null }.map { seamsDraw(session, it, ObjectShading(), camera.toCameraFrom(target)) }
        val pixels = seamsRender("seams-soft-cross-socket", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, draws))), errors)
        val blue = (assertNotNull(session.document.value.character.palette.color(SOCK)).blue * 255f).toInt()
        val lastOnLeg = assertNotNull(camera.project(onLeg(0.1f).toGpuVec(), SEAMS_SIZE, SEAMS_SIZE))
        val torsoPatch = body.surface.patches.first { it.slices != null }
        val firstOnTorso = body.origin + body.surface.shown(torsoPatch.surfaceAt(onTorso.first().first, onTorso.first().second).position)
        val firstOnBody = assertNotNull(camera.project(firstOnTorso.toGpuVec(), SEAMS_SIZE, SEAMS_SIZE))
        val shown = (0..CROSSING_SAMPLES).map { i ->
            val at = lastOnLeg + (firstOnBody - lastOnLeg) * (i.toFloat() / CROSSING_SAMPLES)
            pixels[at.y.toInt() * SEAMS_SIZE + at.x.toInt()] and 0xFF
        }
        println("SOFT CROSSING blue ${shown.joinToString(" ")} of $blue")
        assertTrue(shown.all { kotlin.math.abs(it - blue) <= CROSSING_TOLERANCE }, "the stroke's middle holds its color across the socket: $shown of $blue")
        finish()
    }

    @Test
    fun aDabOverTheClosedEndCoversItsPole() {
        val session = seamsSession(scope, listOf(PartInstance(LEFT_ID, seamsRef("leg"))))
        val dab = BrushStroke(BrushStrokeId("tip"), listOf(Anchor(LEFT_ID, u = 0.985f, v = 0.1f)), SOCK, 0.3f)
        val rim = BrushStroke(BrushStrokeId("rim"), listOf(Anchor(LEFT_ID, u = 0.96f, v = 0.6f)), SPOT, 0.09f)
        (seamsPalette() + SetBaseSlot(LEFT_ID, FUR) + SetBrushStroke(LEFT_ID, dab.id, dab) + SetBrushStroke(LEFT_ID, rim.id, rim)).forEach(session::send)
        session.settled()
        val leg = assertNotNull(session.assembly.output.value?.part(LEFT_ID))
        val patch = leg.surface.patches.first { it.slices != null }
        assertTrue(patch.isPole(patch.nu - 1), "the leg's end closes on a pole")
        val tip = leg.origin + leg.surface.shown(patch.point(patch.index(patch.nu - 1, 0)))
        val distance = 0.75f
        val toCamera = (leg.surface.shown(patch.surfaceAt(0.9f, 0f).alongU.normalized()) + Vec3(0.35f, 0f, 0.3f)).normalized()
        val camera = CameraView(eye = (tip + toCamera * distance).toGpuVec(), target = tip.toGpuVec())
        session.showColorAt(pixelsPerUnit(distance))
        session.settled()
        val draw = seamsDraw(session, leg, ObjectShading(), toCamera)
        seamsRender("seams-pole", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, listOf(draw)))), errors)
        finish()
    }

    @Test
    fun aPadShowsItsSlotAndTakesPaintOnItsGrid() {
        val session = seamsSession(scope, listOf(PartInstance(LEFT_ID, seamsRef("paw"))))
        val dab = BrushStroke(BrushStrokeId("mark"), listOf(Anchor(LEFT_ID, "pad:palm", u = 0.8f, v = 0.2f)), CREAM, 0.05f)
        (seamsPalette() + SetBaseSlot(LEFT_ID, FUR) + SetBrushStroke(LEFT_ID, dab.id, dab)).forEach(session::send)
        session.settled()
        val paw = assertNotNull(session.assembly.output.value?.part(LEFT_ID))
        val distance = 1.0f
        val camera = cameraOn(paw, 0.45f, 0.5f, distance, lift = 0.1f)
        session.showColorAt(pixelsPerUnit(distance))
        session.settled()
        val center = assertNotNull(paw.box).center
        val draw = seamsDraw(session, paw, ObjectShading(), camera.toCameraFrom(center))
        val layers = assertNotNull(session.bodyColor.output.value).parts.getValue(LEFT_ID).layers
        assertEquals(setOf(PAD, CREAM), layers.map { it.slot }.toSet())
        seamsRender("seams-pad", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, listOf(draw)))), errors)
        finish()
    }

    @Test
    fun paintFollowsAnOversketch() {
        val session = seamsSession(scope, listOf(PartInstance(LEFT_ID, seamsRef("leg"))))
        (seamsPalette() + seamsPaint(LEFT_ID)).forEach(session::send)
        session.send(SetOutline(LEFT_ID, View.SIDE, OVERSKETCHED))
        session.settled()
        val leg = assertNotNull(session.assembly.output.value?.part(LEFT_ID))
        val distance = assertNotNull(leg.box).radius * 2.6f
        val camera = cameraOn(leg, 0.5f, 0f, distance)
        session.showColorAt(pixelsPerUnit(distance))
        session.settled()
        val draw = seamsDraw(session, leg, ObjectShading(Shading.TWO_TONE), camera.toCameraFrom(assertNotNull(leg.box).center))
        seamsRender("seams-oversketched", SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, listOf(draw)))), errors)
        finish()
    }
}
