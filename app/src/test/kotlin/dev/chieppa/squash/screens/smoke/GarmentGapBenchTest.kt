package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.squash.screens.person.ARM_L
import dev.chieppa.squash.screens.person.personDressedDocument
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

/**
 * What a pose leaves showing under the dressed fennec's and the dressed person's clothes, read on the posed body the session shows. A
 * vertex is seen when rays from it along at least [SEEN_WAYS] of [DIRECTIONS] leave the character crossing no face it shows. A hole is a vertex of
 * the body or of an inner layer that no shown face has as a corner and that is seen: a look through to where nothing is drawn. Shown
 * again is a vertex hidden at rest that a shown face has as a corner in the pose and is seen: skin or an inner layer the pose carries
 * out. A pose counts only the holes that were not holes at rest. Numbers go to the test's output.
 */
@Tag("slow")
class GarmentGapBenchTest {
    private val work = TestWork()

    private val fennecPoses = listOf(
        "knee 60" to Pose(listOf(BonePose(FRONT_LEG_L, "knee", 60f))),
        "knee 110" to Pose(listOf(BonePose(FRONT_LEG_L, "knee", 110f))),
        "neck 20" to Pose(listOf(BonePose(HEAD, "neck", 20f))),
        "neck 40" to Pose(listOf(BonePose(HEAD, "neck", 40f))),
    )

    private val personPoses = listOf(
        "elbow 90" to Pose(listOf(BonePose(ARM_L, "elbow", 90f))),
        "elbow 120" to Pose(listOf(BonePose(ARM_L, "elbow", 120f))),
        "elbow 150" to Pose(listOf(BonePose(ARM_L, "elbow", 150f))),
        "shoulder 80" to Pose(listOf(BonePose(ARM_L, "shoulder", 80f))),
    )

    @Test
    fun theDressedFennec() = measure("dressed fennec", fennecDressedDocument(), fennecPoses)

    @Test
    fun theDressedPerson() = measure("dressed person", personDressedDocument(), personPoses)

    private fun measure(character: String, document: SquashDocument, poses: List<Pair<String, Pose>>) {
        val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        SessionPose(session)
        work.settle(session)
        val restBody = assertNotNull(session.posedBody.output.value)
        val hiddenAtRest = restBody.parts.flatMap { part -> hiddenOf(part).map { requireNotNull(part.id).value to it } }.toSet()
        val rest = gapsOf(restBody, hiddenAtRest)
        println("$character at rest: ${hiddenAtRest.size} hidden, ${rest.holes.size} of them seen; by part ${byPart(rest.holes)}; hidden by part ${byPart(hiddenAtRest)}")
        for ((name, set) in poses) {
            session.send(SetPose(set))
            work.settle(session)
            val gaps = gapsOf(assertNotNull(session.posedBody.output.value), hiddenAtRest)
            val holes = gaps.holes - rest.holes
            println("$character, $name: ${holes.size} holes, ${gaps.shown.size} shown again; holes by part ${byPart(holes)}; shown again by part ${byPart(gaps.shown)}")
        }
    }

    private class Gaps(val holes: Set<Pair<String, Int>>, val shown: Set<Pair<String, Int>>)

    private fun byPart(vertices: Set<Pair<String, Int>>): Map<String, Int> = vertices.groupingBy { it.first }.eachCount()

    private fun measured(part: PlacedPart): Boolean = part.id != null && part.hanging == null

    private fun hiddenOf(part: PlacedPart): List<Int> {
        if (!measured(part)) return emptyList()
        val mesh = part.surface.mesh
        val used = BooleanArray(mesh.vertexCount).also { flags -> mesh.indices.forEach { flags[it] = true } }
        return used.indices.filter { !used[it] }
    }

    private fun gapsOf(body: AssembledCharacter, hiddenAtRest: Set<Pair<String, Int>>): Gaps {
        val grid = RayGrid(shownCorners(body))
        val holes = mutableSetOf<Pair<String, Int>>()
        val shown = mutableSetOf<Pair<String, Int>>()
        for (part in body.parts.filter(::measured)) {
            val id = requireNotNull(part.id).value
            val mesh = part.surface.mesh
            val hidden = hiddenOf(part).toSet()
            for (v in 0 until mesh.vertexCount) {
                val wasHidden = (id to v) in hiddenAtRest
                if (v !in hidden && !wasHidden) continue
                val p = part.origin + mesh.position(v)
                val seen = DIRECTIONS.count { grid.escapes(p, it) } >= SEEN_WAYS
                if (v in hidden && seen) holes += id to v
                if (v !in hidden && seen) shown += id to v
            }
        }
        return Gaps(holes, shown)
    }

    /** The corners of every face [body] shows, in body space, three points a face. */
    private fun shownCorners(body: AssembledCharacter): FloatArray {
        val all = body.parts.filter { it.id != null }
        val corners = FloatArray(all.sumOf { it.surface.mesh.indices.size } * 3)
        var at = 0
        for (part in all) for (index in part.surface.mesh.indices) {
            val p = part.origin + part.surface.mesh.position(index)
            corners[at++] = p.x
            corners[at++] = p.y
            corners[at++] = p.z
        }
        return corners
    }

    private companion object {
        /** How many ways out a vertex is looked at along. */
        const val WAYS = 96

        /** How many of [WAYS] must leave the character for a vertex to be seen, so a look down a long sleeve or through a slit is not counted. */
        const val SEEN_WAYS = 10

        /** Ways out spread evenly over the sphere. */
        val DIRECTIONS: List<Vec3> = List(WAYS) { i ->
            val y = 1f - 2f * (i + 0.5f) / WAYS
            val r = sqrt(1f - y * y)
            val turn = i * GOLDEN_TURN
            Vec3(r * cos(turn), y, r * sin(turn))
        }

        /** The golden angle, in radians. */
        const val GOLDEN_TURN = 2.3999631f
    }
}
