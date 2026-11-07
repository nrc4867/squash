package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Places that lie on a grid's surface are this close to one of its points, as a share of its longest cell edge. */
private const val ON_SURFACE = 0.75f

/** A place on a bent part has moved at least this far from where it rests, in body units. */
private const val MOVED = 0.02f

/** Body units a place on a part that stays at rest may move, for rounding. */
private const val STILL = 1e-5f

@OptIn(ExperimentalCoroutinesApi::class)
class PosedSurfaceTest {
    private val leg = PartId("back-leg-l")
    private val torso = PartId("torso")
    private val tail = PartId("tail")
    private val stroke = MarkId("leg-stroke")
    private val kneeBent = Pose(listOf(BonePose(leg, "knee", 70f)))

    private fun TestScope.posedSession(): Pair<CharacterSession, SessionPose> {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        runCurrent()
        check(session.graph.settled.value)
        return session to pose
    }

    private fun CharacterSession.pose(pose: Pose, scope: TestScope) {
        send(SetPose(pose))
        scope.runCurrent()
        check(graph.settled.value)
    }

    /** Every point of [part]'s posed mesh in body space, and its longest grid cell edge there, along or around. */
    private fun surfaceOf(posed: SessionPose, part: PlacedPart): Pair<List<Vec3>, Float> {
        val positions = checkNotNull(posed.posed.output.value).parts.getValue(checkNotNull(part.id))
        val points = List(positions.size / 3) { Vec3(positions[it * 3], positions[it * 3 + 1], positions[it * 3 + 2]) }
        val cell = part.surface.patches.maxOf { patch ->
            (0 until patch.nu - 1).maxOf { ring ->
                (0 until patch.nv).maxOf { v ->
                    val along = patch.point(patch.index(ring + 1, v)) - patch.point(patch.index(ring, v))
                    val around = patch.point(patch.index(ring, (v + 1) % patch.nv)) - patch.point(patch.index(ring, v))
                    maxOf(along.length, around.length)
                }
            }
        }
        return points to cell
    }

    private fun assertOn(points: List<Vec3>, surface: Pair<List<Vec3>, Float>, what: String) {
        val (mesh, cell) = surface
        for (point in points) {
            val nearest = mesh.minOf { (it - point).length }
            assertTrue(nearest <= cell * ON_SURFACE, "$what at $point lies $nearest from the posed surface, past ${cell * ON_SURFACE}")
        }
    }

    @Test
    fun theRestPoseGivesWhatLiesAtRestItself() = runTest {
        val (session, _) = posedSession()
        assertSame(session.assembly.output.value, session.posedBody.output.value)
        assertSame(session.bodyLines.output.value, session.posedLines.output.value)
        assertSame(session.bodySurfaceWork.output.value, session.posedSurfaceWork.output.value)
        assertSame(session.bodyFur.output.value, session.posedFur.output.value)
        val lines = session.bodyLines.output.value
        session.pose(kneeBent, this)
        assertNotSame(lines, session.posedLines.output.value)
        session.send(ResetPose)
        runCurrent()
        assertSame(lines, session.posedLines.output.value)
        assertSame(session.bodyFur.output.value, session.posedFur.output.value)
    }

    @Test
    fun aBentLegsLinesLieOnItsPosedSurfaceAndTheTorsosStay() = runTest {
        val (session, posed) = posedSession()
        val rest = checkNotNull(session.bodyLines.output.value)
        session.pose(kneeBent, this)
        val lines = checkNotNull(session.posedLines.output.value)
        assertSame(rest.parts[torso], lines.parts[torso])
        val bent = lines.parts.getValue(leg)
        val surface = surfaceOf(posed, bent.part)
        val toViewer = { _: Vec3 -> Vec3(0f, 0f, -1f) }
        val contour = bent.grids.flatMap { grid -> grid.contours(toViewer).flatMap { run -> run.points.map { it.position } } }
        assertTrue(contour.isNotEmpty())
        assertOn(contour, surface, "A contour point")
        val restContour = rest.parts.getValue(leg).grids.flatMap { grid -> grid.contours(toViewer).flatMap { run -> run.points.map { it.position } } }
        assertTrue(contour.zip(restContour).any { (a, b) -> (a - b).length > MOVED }, "The leg's contour stays where it rests")
        val paw = lines.parts.getValue(PartId("back-paw-l"))
        val crease = paw.crease.flatMap { run -> run.points.map { it.position } }
        assertTrue(crease.isNotEmpty())
        assertOn(crease, surfaceOf(posed, paw.part), "A crease point on the paw")
        assertOn(crease, surface, "A crease point at the leg the paw leaves")
    }

    @Test
    fun aCreaseAtABentHipStaysOnTheOverlap() = runTest {
        val (session, posed) = posedSession()
        session.pose(Pose(listOf(BonePose(leg, "hip", -35f))), this)
        val lines = checkNotNull(session.posedLines.output.value)
        val crease = lines.parts.getValue(leg).crease.flatMap { run -> run.points.map { it.position } }
        assertTrue(crease.isNotEmpty())
        assertOn(crease, surfaceOf(posed, lines.parts.getValue(leg).part), "A crease point on the leg")
        assertOn(crease, surfaceOf(posed, lines.parts.getValue(torso).part), "A crease point at the torso")
    }

    @Test
    fun aStrokesPointsFollowTheBentPart() = runTest {
        val (session, posed) = posedSession()
        val anchors = (0..6).map { Anchor(leg, BODY_PATCH, 0.45f + it * 0.05f, 0.25f) }
        session.send(AddMarks(leg, listOf(Mark(stroke, anchors))))
        runCurrent()
        val rest = checkNotNull(session.bodySurfaceWork.output.value).parts.getValue(leg).marks.first { it.id == stroke }.points
        session.pose(kneeBent, this)
        val work = checkNotNull(session.posedSurfaceWork.output.value)
        val points = work.parts.getValue(leg).marks.first { it.id == stroke }.points
        assertOn(points.map { it.position }, surfaceOf(posed, checkNotNull(session.posedBody.output.value?.part(leg))), "A stroke point")
        assertTrue(points.zip(rest).any { (a, b) -> (a.position - b.position).length > MOVED }, "The stroke stays where it rests")
        assertSame(checkNotNull(session.bodySurfaceWork.output.value).parts[torso], work.parts[torso])
    }

    @Test
    fun furRootsStandOnTheBentTail() = runTest {
        val (session, posed) = posedSession()
        val rest = checkNotNull(session.bodyFur.output.value)
        session.pose(Pose(listOf(BonePose(tail, "root", 40f))), this)
        val fur = checkNotNull(session.posedFur.output.value)
        val roots = fur.parts.getValue(tail).roots
        val restRoots = rest.parts.getValue(tail).roots
        assertTrue(roots.isNotEmpty())
        assertTrue(roots.map { it.id } == restRoots.map { it.id } && roots.zip(restRoots).all { (a, b) -> a.room == b.room }, "The strokes keep their order and room")
        val body = checkNotNull(session.posedBody.output.value)
        val marks = checkNotNull(session.posedSurfaceWork.output.value).parts.getValue(tail).marks.associateBy { it.id }
        for (root in roots) {
            val anchor = marks.getValue(root.id).points.first().anchor
            val part = checkNotNull(body.part(anchor.part))
            val lifted = root.position - root.normal * anchor.offset
            assertOn(listOf(lifted), surfaceOf(posed, part), "A fur root on ${anchor.part}")
        }
        assertTrue(roots.zip(restRoots).any { (a, b) -> (a.position - b.position).length > MOVED }, "The tail's fur stays where it rests")
        val still = rest.parts.keys.filter { it != tail && it != torso }
        for (part in still) {
            fur.parts.getValue(part).roots.zip(rest.parts.getValue(part).roots).forEach { (a, b) -> assertTrue((a.position - b.position).length < STILL, "$part's fur moved") }
        }
    }
}
