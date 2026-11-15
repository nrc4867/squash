package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.rig.StackFixture.leg
import dev.chieppa.squash.rig.StackFixture.paw
import dev.chieppa.squash.rig.StackFixture.stackSession
import dev.chieppa.squash.rig.StackFixture.torso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurfacePairingTest {
    private val slot = GarmentSlot(GarmentSlotId("body"), "Body")
    private val winter = OutfitId("winter")
    private val mitts = OutfitId("mitts")

    /** Two outfits over the leg: a sweater, and a sweater that hides the paw, so each outfit's body is meshed apart. */
    private val wardrobe = Wardrobe(
        listOf(
            Garment(GarmentId("sweater"), "Sweater", slot.id, branches = listOf(GarmentBranch(torso.id, 0.1f, 0.6f), GarmentBranch(leg.id, 0f, 0.5f))),
            Garment(
                GarmentId("mitt"), "Mitt", slot.id,
                branches = listOf(GarmentBranch(torso.id, 0.1f, 0.6f), GarmentBranch(leg.id, 0f, 0.9f)),
                changes = mapOf(paw.id to BodyPartChange.Hide),
            ),
        ),
        listOf(slot),
        listOf(Outfit(winter, "Winter", mapOf(slot.id to GarmentId("sweater"))), Outfit(mitts, "Mitts", mapOf(slot.id to GarmentId("mitt")))),
        winter,
    )

    private class Shown(val surface: CharacterSurface, val paint: HeldPaint?, val dress: SurfaceDress?)

    @Test
    fun aSurfaceIsShownOnlyWithThePaintAndDressLaidOnIt() = runTest {
        val session = stackSession(withPaw = true)
        session.send(SetWardrobe(wardrobe))
        runCurrent()
        val seen = ArrayList<Shown>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            combine(session.characterSurface.output, session.surfacePaint.output, session.oneSurface.dress.output) { surface, paint, dress ->
                surface?.surface?.let { Shown(it, paint, dress?.dress) }
            }.collect { it?.let(seen::add) }
        }
        for (outfit in listOf(mitts, winter, mitts)) {
            session.send(ShowOutfit(outfit))
            runCurrent()
        }
        assertTrue(seen.any { it.dress?.isOn(it.surface) == false }, "an outfit's surface settles before its dress, beside the last outfit's dress")
        for (shown in seen) {
            val laid = PosedSurface.laid(shown.surface, shown.paint, shown.dress) ?: continue
            laid.dress?.skin?.mesh(laid.surface.render)
            laid.paint?.layers?.forEach { it.on(laid.surface.render) }
        }
        val settled = seen.last()
        assertNotNull(PosedSurface.laid(settled.surface, settled.paint, settled.dress), "the settled surface wears its own dress")
    }

    @Test
    fun aPosedOutfitSwitchPosesOnlyTheDressAndPaintLaidOnTheSurface() = runTest {
        val session = stackSession(withPaw = true)
        SessionPose(session)
        session.send(SetWardrobe(wardrobe))
        session.send(SetPose(Pose(listOf(BonePose(leg.id, "knee", 60f)))))
        runCurrent()
        val posed = assertNotNull(session.oneSurface.posed.value)
        val failures = ArrayList<Throwable>()
        val shownPosed = ArrayList<PosedSurface>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { posed.failure.collect { it?.let(failures::add) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { posed.output.collect { it?.posed?.let(shownPosed::add) } }
        for (outfit in listOf(mitts, winter, mitts)) {
            session.send(ShowOutfit(outfit))
            runCurrent()
        }
        assertTrue(failures.isEmpty(), failures.joinToString { it.toString() })
        for (shown in shownPosed) assertTrue(shown.dress?.isOn(shown.surface) != false)
    }

    @Test
    fun skinPushedThroughAPoseThatPartsQuadsCutsThePaintAtRest() = runTest {
        val session = stackSession()
        session.send(SetBaseSlot(torso.id, SlotName("fur")))
        session.send(SetBaseSlot(leg.id, SlotName("sock")))
        runCurrent()
        val rest = assertNotNull(session.characterSurface.output.value?.surface)
        val dress = assertNotNull(session.oneSurface.dress.output.value?.dress)
        val paint = assertNotNull(session.surfacePaint.output.value?.paint)
        assertTrue(paint.layers.any { it.candidates.isNotEmpty() })
        val cell = rest.mesh.cell
        val positions = rest.positions.copyOf()
        val normals = rest.lineNormals
        for (i in positions.indices) positions[i] += normals[i] * cell * 3f
        val width = RenderMesh.FLOATS_PER_VERTEX
        val vertices = rest.render.vertices.copyOf()
        for (v in 0 until rest.mesh.vertexCount) for (c in 0 until 3) vertices[v * width + c] = positions[v * 3 + c]
        val parted = rest.render.indices.copyOfRange(0, rest.render.indices.size / 2)
        val body = rest.movedTo(RenderMesh(vertices, parted), positions, rest.lineNormals)
        val (shownPaint, worn) = PosedHiding(body, rest, dress.garments, dress).of(paint)
        assertTrue(worn.shown !== dress.shown, "the skin pushed out through the sweater is hidden")
        assertTrue(assertNotNull(shownPaint).layers.isNotEmpty())
    }
}
