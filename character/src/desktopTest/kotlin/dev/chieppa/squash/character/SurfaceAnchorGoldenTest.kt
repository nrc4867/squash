package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetExpression
import dev.chieppa.squash.document.SetExpressionMarks
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.golden.GoldenFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private const val MESH_CLOSE = 1e-5f

/** Anchors held fixed through oversketches of a part and of its parent: what they render at follows the mesh. */
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceAnchorGoldenTest {
    private val fur = SlotName("fur")
    private val eye = MarkGroupId("eye")
    private val glad = ExpressionId("glad")
    private val legOversketch = outline(0f to 0.2f, 0.6f to 0.08f, 0.6f to -0.12f, 0f to -0.2f)
    private val torsoOversketch = outline(0f to 0.3f, 1.2f to 0.35f, 1.2f to -0.25f, 0f to -0.3f)

    /** An anchor on the vertex at [ring] and [segment] of [part]'s body. */
    private fun CharacterSession.onVertex(part: PartInstance, ring: Int, segment: Int): Anchor {
        val patch = builtSurface(part).body()
        return on(part, assertNotNull(patch.slices)[ring].u, segment.toFloat() / patch.nv)
    }

    private fun CharacterSession.draw(points: List<Anchor>) {
        val across = listOf(points[0], points[1], on(torsoPart, 0.8f, 0.25f))
        val sock = line("sock", *points.toTypedArray())
        send(SetMark(leftLeg.id, sock.id, sock))
        send(SetRegion(leftLeg.id, RegionId("patch"), Region(RegionId("patch"), across, fur)))
        send(SetBrushStroke(leftLeg.id, BrushStrokeId("dab"), BrushStroke(BrushStrokeId("dab"), points, fur, 0.05f)))
        send(SetMarkGroup(leftLeg.id, eye, MarkGroup(eye, "Eye", listOf(GroupRole(MarkRole.LID)))))
        send(SetExpression(glad, ExpressionSet(glad, "Glad")))
        send(SetExpressionMarks(glad, leftLeg.id, eye, MarkRole.LID, listOf(line("lid", points[0], points[2]))))
    }

    private fun CharacterSession.assertOnMesh(part: PartInstance, points: List<ResolvedPoint>, vertices: List<Pair<Int, Int>>) {
        val surface = builtSurface(part)
        val patch = surface.body()
        val origin = assertNotNull(assembly.output.value?.part(part.id)).origin
        points.zip(vertices).forEach { (point, vertex) ->
            val (ring, segment) = vertex
            assertClose(origin + surface.shown(patch.point(patch.index(ring, segment))), point.position, MESH_CLOSE)
        }
    }

    @Test
    fun anchorsHeldFixedFollowTheMeshThroughOversketches() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val vertices = listOf(12 to 3, 24 to 6, 36 to 9)
        val anchors = vertices.map { (ring, segment) -> session.onVertex(leftLeg, ring, segment) }
        session.draw(anchors)
        session.settle()

        fun marks(part: PartInstance) = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(part.id)).marks.single().points
        val stages = mutableListOf<String>()
        fun record(name: String) {
            session.assertOnMesh(leftLeg, marks(leftLeg), vertices)
            session.assertOnMesh(rightLeg, marks(rightLeg), vertices)
            assertEquals(anchors, marks(leftLeg).map { it.anchor }, "the document's anchors never move")
            stages += "$name\n${fixtureOf(assertNotNull(session.bodySurfaceWork.output.value))}"
        }

        record("drawn")
        val drawn = marks(leftLeg).map { it.position }
        session.send(SetOutline(leftLeg.id, View.SIDE, legOversketch))
        session.settle()
        record("leg oversketched")
        val refit = marks(leftLeg).map { it.position }
        assertNotEquals(drawn, refit)
        session.send(SetOutline(torsoPart.id, View.SIDE, torsoOversketch))
        session.settle()
        record("torso oversketched")
        assertNotEquals(refit, marks(leftLeg).map { it.position })

        GoldenFixture.check(Path.of("src/desktopTest/golden/surface-anchors.txt"), stages.joinToString(""), "character")
    }

    /** Each part's work, as lines of whole numbers of [FIXTURE_STEP]. */
    private fun fixtureOf(work: BodySurfaceWork): String = buildString {
        for ((part, held) in work.parts) {
            append("part ${part.value}\n")
            held.marks.forEach { append(line("mark ${it.id.value}", it.points)) }
            held.regions.forEach { append(line("region ${it.id.value}", it.border)) }
            held.strokes.forEach { append(line("stroke ${it.id.value}", it.points)) }
            held.expressions.forEach { set ->
                set.marks.forEach { (group, roles) ->
                    roles.forEach { (role, marks) ->
                        marks.forEach { append(line("expression ${set.name} ${group.value} $role ${it.id.value}", it.points)) }
                    }
                }
            }
        }
    }

    private fun line(label: String, points: List<ResolvedPoint>) =
        "$label ${points.joinToString(" ") { "${text(it.position)}/${text(it.normal)}" }}\n"

    private fun text(v: Vec3) = "${(v.x / FIXTURE_STEP).roundToInt()},${(v.y / FIXTURE_STEP).roundToInt()},${(v.z / FIXTURE_STEP).roundToInt()}"
}
