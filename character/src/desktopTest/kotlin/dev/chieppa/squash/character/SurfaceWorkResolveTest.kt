package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.SurfaceWorkInput
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.character.build.resolveSurfaceWork
import dev.chieppa.squash.character.build.surfaceOf
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkVersion
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetExpression
import dev.chieppa.squash.document.SetExpressionMarks
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceWorkResolveTest {
    private val fur = SlotName("fur")

    private fun onLeg(part: PartInstance) = listOf(on(part, 0.3f, 0.2f), on(part, 0.5f, 0.3f), on(part, 0.7f, 0.4f))

    @Test
    fun everyItemKeepsItsIdsNamesAndOrderAtPointsOnTheSurface() = runTest {
        val session = surfaceSession(torsoPart)
        val points = onLeg(torsoPart)
        val eye = MarkGroupId("eye")
        val glad = ExpressionId("glad")
        val commands = listOf(
            SetMark(torsoPart.id, MarkId("second"), line("second", *points.toTypedArray())),
            SetMark(torsoPart.id, MarkId("first"), Mark(MarkId("first"), points, ink = false, cameraLocked = true, versions = mapOf(CameraBin("side") to MarkVersion(points.reversed()))), index = 1),
            SetMarkGroup(torsoPart.id, eye, MarkGroup(eye, "Eye", listOf(GroupRole(MarkRole.LID, marks = listOf(MarkId("first")))))),
            SetRegion(torsoPart.id, RegionId("patch"), Region(RegionId("patch"), points, fur, ink = true)),
            SetBrushStroke(torsoPart.id, BrushStrokeId("dab"), BrushStroke(BrushStrokeId("dab"), points.take(1), fur, 0.1f)),
            SetExpression(glad, ExpressionSet(glad, "Glad")),
            SetExpressionMarks(glad, torsoPart.id, eye, MarkRole.LID, listOf(line("lid", *points.toTypedArray()))),
        )
        commands.forEach(session::send)
        session.settle()

        val work = assertNotNull(session.surfaceWork(torsoPart.id).output.value)
        val patch = session.builtSurface(torsoPart).body()
        val expected = points.map { patch.positionAt(it.u, it.v) }
        assertEquals(listOf("second", "first"), work.marks.map { it.id.value })
        assertEquals(expected, work.marks[0].points.map { it.position })
        assertEquals(points, work.marks[0].points.map { it.anchor })
        assertEquals(points.map { patch.surfaceAt(it.u, it.v).normal }, work.marks[0].points.map { it.normal })
        assertEquals(false, work.marks[1].ink)
        assertEquals(true, work.marks[1].cameraLocked)
        assertEquals(expected.reversed(), work.marks[1].versions.getValue(CameraBin("side")).points.map { it.position })
        assertEquals("Eye", work.groups.single().name)
        assertEquals(expected, work.regions.single().border.map { it.position })
        assertEquals(fur, work.regions.single().slot)
        assertEquals(expected.take(1), work.strokes.single().points.map { it.position })
        assertEquals(0.1f, work.strokes.single().radius)
        val expression = work.expressions.single()
        assertEquals(glad to "Glad", expression.id to expression.name)
        assertEquals(expected, expression.marks.getValue(eye).getValue(MarkRole.LID).single().points.map { it.position })
    }

    @Test
    fun anOffsetLiesAlongTheOutwardNormal() = runTest {
        val session = surfaceSession(leftLeg, rightLeg)
        val anchors = listOf(on(leftLeg, 0.5f, 0.2f), on(leftLeg, 0.5f, 0.2f, offset = 0.05f))
        session.send(SetMark(leftLeg.id, MarkId("whisker"), Mark(MarkId("whisker"), anchors)))
        session.settle()

        for (part in listOf(leftLeg, rightLeg)) {
            val (flat, raised) = assertNotNull(session.surfaceWork(part.id).output.value).marks.single().points
            assertClose(flat.position + flat.normal * 0.05f, raised.position, 1e-5f)
            val surface = session.builtSurface(part)
            val center = surface.shown(assertNotNull(surface.body().slices).first { it.u >= 0.5f }.frame.center)
            assertTrue((raised.position - center).length > (flat.position - center).length, "a positive offset stands off the surface")
        }
    }

    @Test
    fun theNormalIsFiniteAndOutwardOnEveryShippedTemplate() {
        val built = shipped.parts.map { it to PartData() } + (template("paw") to PartData(params = mapOf("toes" to 4f)))
        for ((template, data) in built) {
            val surface = surfaceOf(template, data, mirrored = false)
            for (patch in surface.patches.filter { it.slices != null }) {
                val rings = assertNotNull(patch.slices)
                for (ring in listOf(0, rings.size / 2, rings.size - 1)) {
                    for (v in listOf(0f, 0.2f, 0.5f, 0.8f)) {
                        val point = assertNotNull(surface.pointAt(Anchor(PartId(template.id.value), patch.name, rings[ring].u, v)))
                        assertTrue(point.normal.isFinite(), "${template.id.value} ${patch.name} ring $ring v $v: ${point.normal}")
                        if (ring == rings.size / 2) {
                            val outward = point.normal dot (point.position - rings[ring].frame.center)
                            assertTrue(outward > 0f, "${template.id.value} ${patch.name} v $v faces in by $outward")
                        }
                    }
                }
            }
        }
    }

    private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    @Test
    fun theRightSideMirrorsTheLeftOnItsOwnTwin() = runTest {
        val session = surfaceSession(leftLeg, rightLeg)
        session.send(SetMark(leftLeg.id, MarkId("sock"), Mark(MarkId("sock"), onLeg(leftLeg))))
        session.settle()

        val left = assertNotNull(session.surfaceWork(leftLeg.id).output.value).marks.single().points
        val right = assertNotNull(session.surfaceWork(rightLeg.id).output.value).marks.single().points
        assertEquals(setOf(leftLeg.id), left.mapTo(hashSetOf()) { it.anchor.part })
        assertEquals(setOf(rightLeg.id), right.mapTo(hashSetOf()) { it.anchor.part })
        assertEquals(onLeg(leftLeg).map { it.v }, right.map { it.anchor.v }, "a mirrored twin keeps v")
        left.zip(right).forEach { (l, r) ->
            assertClose(l.position.mirrored, r.position, 1e-5f)
            assertClose(l.normal.mirrored, r.normal, 1e-5f)
        }
        assertTrue(left.first().normal.z != 0f)
    }

    @Test
    fun aBorderAcrossASocketLandsOnEachPartItNamesInBodySpace() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        val border = listOf(on(leftLeg, 0.2f, 0.2f), on(leftLeg, 0.2f, 0.7f), on(torsoPart, 0.8f, 0.25f))
        session.send(SetRegion(leftLeg.id, RegionId("sock"), Region(RegionId("sock"), border, fur)))
        session.settle()

        val assembled = assertNotNull(session.assembly.output.value)
        val body = assertNotNull(session.bodySurfaceWork.output.value)
        val left = assertNotNull(body.parts[leftLeg.id]).regions.single().border
        val right = assertNotNull(body.parts[rightLeg.id]).regions.single().border
        assertEquals(listOf(leftLeg.id, leftLeg.id, torsoPart.id), left.map { it.anchor.part })
        assertEquals(listOf(rightLeg.id, rightLeg.id, torsoPart.id), right.map { it.anchor.part })
        assertEquals(0.75f, right.last().anchor.v, "a part in the middle of the body reads 1 - v from the right")

        val legOrigin = assertNotNull(assembled.part(leftLeg.id)).origin
        val legPatch = session.builtSurface(leftLeg).body()
        assertClose(legOrigin + legPatch.positionAt(0.2f, 0.2f), left[0].position, 1e-5f)
        assertClose(session.builtSurface(torsoPart).body().positionAt(0.8f, 0.25f), left[2].position, 1e-5f)
        left.zip(right).forEach { (l, r) ->
            assertClose(l.position.mirrored, r.position)
            assertClose(l.normal.mirrored, r.normal)
        }
    }

    @Test
    fun aDanglingAnchorDropsAndAnItemTooShortDropsWithIt() = runTest {
        val paw = PartInstance(PartId("paw-1"), templateRef("paw"), SocketRef(torsoPart.id, "neck"))
        val session = surfaceSession(torsoPart, paw)
        val good = onLeg(torsoPart)
        val noPart = Anchor(PartId("gone"), u = 0.5f, v = 0.5f)
        val noPatch = on(torsoPart, 0.5f, 0.5f, patch = "wing")
        val eye = MarkGroupId("eye")
        val glad = ExpressionId("glad")
        val commands = listOf(
            SetMark(torsoPart.id, MarkId("kept"), Mark(MarkId("kept"), listOf(good[0], noPart, good[1], noPatch), versions = mapOf(CameraBin("side") to MarkVersion(listOf(good[0], noPart))))),
            SetMark(torsoPart.id, MarkId("lost"), line("lost", good[0], noPart)),
            SetMarkGroup(torsoPart.id, eye, MarkGroup(eye, "Eye", listOf(GroupRole(MarkRole.LID, marks = listOf(MarkId("lost"), MarkId("kept")))))),
            SetRegion(torsoPart.id, RegionId("kept"), Region(RegionId("kept"), good + noPatch, fur)),
            SetRegion(torsoPart.id, RegionId("lost"), Region(RegionId("lost"), good.take(2) + noPatch, fur)),
            SetBrushStroke(torsoPart.id, BrushStrokeId("lost"), BrushStroke(BrushStrokeId("lost"), listOf(noPart), fur, 0.1f)),
            SetExpression(glad, ExpressionSet(glad, "Glad")),
            SetExpressionMarks(glad, torsoPart.id, eye, MarkRole.LID, listOf(line("lid", good[0], noPart))),
        )
        commands.forEach(session::send)
        session.settle()

        val node = session.surfaceWork(torsoPart.id)
        assertNull(node.failure.value)
        assertDangling(assertNotNull(node.output.value), good)
        assertNull(session.bodySurfaceWork.failure.value)
        assertDangling(assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(torsoPart.id)), good)
    }

    @Test
    fun anAnchorOnAPadLandsOnItsGrid() = runTest {
        val paw = PartInstance(PartId("paw-1"), templateRef("paw"), SocketRef(torsoPart.id, "neck"))
        val session = surfaceSession(torsoPart, paw)
        session.settle()
        val pad = assertNotNull(session.builtSurface(paw).patches.first { it.name == "pad:palm" })
        assertNull(pad.slices)
        val anchors = listOf(on(paw, 0.5f, 0f, patch = pad.name), on(paw, 0.5f, 0.25f, patch = pad.name))
        session.send(SetMark(paw.id, MarkId("on-pad"), Mark(MarkId("on-pad"), anchors)))
        session.settle()
        val points = assertNotNull(session.surfaceWork(paw.id).output.value).marks.single().points
        assertEquals(anchors, points.map { it.anchor })
        assertClose(pad.positionAt(0.5f, 0.25f), points[1].position)
    }

    private fun assertDangling(work: ResolvedSurfaceWork, good: List<Anchor>) {
        val kept = work.marks.single()
        assertEquals("kept", kept.id.value)
        assertEquals(good.take(2), kept.points.map { it.anchor })
        assertEquals(emptyMap(), kept.versions)
        assertEquals(listOf(MarkId("kept")), work.groups.single().roles.single().marks)
        assertEquals("kept", work.regions.single().id.value)
        assertEquals(emptyList(), work.strokes)
        assertEquals(emptyList(), work.expressions.single().marks.values.single().getValue(MarkRole.LID))
    }

    @Test
    fun aRemovedPartsAnchorsDropAndUndoBringsThemBack() = runTest {
        val tail = PartInstance(PartId("tail-1"), templateRef("tail"), SocketRef(torsoPart.id, "tail"))
        val session = surfaceSession(torsoPart, tail)
        val points = listOf(on(torsoPart, 0.3f, 0.2f), on(tail, 0.5f, 0.2f), on(torsoPart, 0.5f, 0.2f))
        session.send(SetMark(torsoPart.id, MarkId("line"), Mark(MarkId("line"), points)))
        session.settle()
        val node = session.surfaceWork(torsoPart.id)
        val whole = assertNotNull(node.output.value)
        assertEquals(3, whole.marks.single().points.size)

        session.send(RemovePart(tail.id))
        session.settle()
        assertEquals(2, assertNotNull(node.output.value).marks.single().points.size)
        assertEquals(setOf(torsoPart.id), session.bodySurfaceWork.output.value?.parts?.keys)

        session.history.undo()
        session.settle()
        assertEquals(whole, node.output.value)
    }

    @Test
    fun aStaleRunStopsAtTheNextItem() {
        val surface = surfaceOf(template("leg"), PartData(), mirrored = false)
        val part = PartId("leg-1")
        val anchors = listOf(Anchor(part, u = 0.3f, v = 0.2f), Anchor(part, u = 0.4f, v = 0.2f), Anchor(part, u = 0.5f, v = 0.2f))
        val marks = List(3) { Mark(MarkId("mark-$it"), anchors) }
        val regions = List(2) { Region(RegionId("region-$it"), anchors, fur) }
        val input = SurfaceWorkInput(SurfaceWork(marks = marks, regions = regions), parts = setOf(part))
        var checks = 0
        assertNotEquals(ResolvedSurfaceWork(), resolveSurfaceWork(input, mapOf(part to surface)) { checks++ })
        assertEquals(5, checks, "one check before each item")

        checks = 0
        assertFailsWith<CancellationException> {
            resolveSurfaceWork(input, mapOf(part to surface)) {
                checks++
                if (checks == 2) throw CancellationException("stale")
            }
        }
        assertEquals(2, checks)
    }
}
