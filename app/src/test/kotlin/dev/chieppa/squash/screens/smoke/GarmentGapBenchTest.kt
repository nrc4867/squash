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
import dev.chieppa.squash.screens.look.measure.SEEN_WAYS
import dev.chieppa.squash.screens.look.measure.WAYS_OUT
import dev.chieppa.squash.screens.look.measure.seen
import dev.chieppa.squash.screens.look.measure.shownCorners
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.womanDressedDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

/**
 * What a pose leaves showing under the dressed fennec's and the dressed woman's clothes, read on the posed body the session shows. A
 * vertex is seen when rays from it along at least [SEEN_WAYS] of [WAYS_OUT] leave the character crossing no face it shows. A hole is
 * a vertex of the body or of an inner layer that a face of its whole mesh has as a corner, that no shown face has, and that is seen: a
 * look through to where nothing is drawn. Shown again is a vertex hidden at rest that a shown face has as a corner in the pose and is
 * seen: skin or an inner layer the pose carries out. A pose counts only the holes that were not holes at rest. Bare skin is a vertex of
 * a body part a garment's branch covers that a shown face has as a corner and that is seen, as between two garments that do not meet.
 * Numbers go to the test's output.
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

    /** The arm's elbow, its second joint, bends the negative way, as far as its limit of 145°; its shoulder, the first, raises it the negative way. */
    private val womanPoses = listOf(
        "elbow 90" to Pose(listOf(BonePose(ARM_L, "joint.2", -90f))),
        "elbow 120" to Pose(listOf(BonePose(ARM_L, "joint.2", -120f))),
        "elbow 145" to Pose(listOf(BonePose(ARM_L, "joint.2", -145f))),
        "shoulder 80" to Pose(listOf(BonePose(ARM_L, "joint.1", -80f))),
    )

    @Test
    fun theDressedFennec() = measure("dressed fennec", fennecDressedDocument(), fennecPoses)

    @Test
    fun theDressedWoman() = measure("dressed woman", womanDressedDocument(), womanPoses)

    private fun measure(character: String, document: SquashDocument, poses: List<Pair<String, Pose>>) {
        val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        SessionPose(session)
        work.settle(session)
        val restBody = assertNotNull(session.posedBody.output.value)
        val hiddenAtRest = restBody.parts.flatMap { part -> hiddenOf(part).map { requireNotNull(part.id).value to it } }.toSet()
        val rest = gapsOf(restBody, hiddenAtRest)
        println("$character at rest: ${hiddenAtRest.size} hidden, ${rest.holes.size} of them seen; by part ${byPart(rest.holes)}; hidden by part ${byPart(hiddenAtRest)}")
        println("$character at rest: ${rest.bare.size} bare skin seen on covered parts; by part ${byPart(rest.bare)}")
        for ((name, set) in poses) {
            session.send(SetPose(set))
            work.settle(session)
            val gaps = gapsOf(assertNotNull(session.posedBody.output.value), hiddenAtRest)
            val holes = gaps.holes - rest.holes
            println("$character, $name: ${holes.size} holes, ${gaps.shown.size} shown again; holes by part ${byPart(holes)}; shown again by part ${byPart(gaps.shown)}")
            println("$character, $name: ${gaps.bare.size} bare skin seen on covered parts; by part ${byPart(gaps.bare)}")
        }
    }

    /** The [holes], the vertices [shown] again, and the [bare] skin seen of the body parts a garment's branch covers, shown and seen. */
    private class Gaps(val holes: Set<Pair<String, Int>>, val shown: Set<Pair<String, Int>>, val bare: Set<Pair<String, Int>>)

    private fun byPart(vertices: Set<Pair<String, Int>>): Map<String, Int> = vertices.groupingBy { it.first }.eachCount()

    private fun measured(part: PlacedPart): Boolean = part.id != null && part.hanging == null

    /** The vertices a face of [part]'s whole mesh has as a corner and no shown face does; a pole's copies no face uses are never hidden. */
    private fun hiddenOf(part: PlacedPart): List<Int> {
        if (!measured(part)) return emptyList()
        val mesh = part.surface.mesh
        val used = BooleanArray(mesh.vertexCount).also { flags -> mesh.indices.forEach { flags[it] = true } }
        val whole = BooleanArray(mesh.vertexCount).also { flags -> (part.hiding?.indices ?: mesh.indices).forEach { flags[it] = true } }
        return used.indices.filter { whole[it] && !used[it] }
    }

    private fun gapsOf(body: AssembledCharacter, hiddenAtRest: Set<Pair<String, Int>>): Gaps {
        val grid = RayGrid(shownCorners(body))
        val holes = mutableSetOf<Pair<String, Int>>()
        val shown = mutableSetOf<Pair<String, Int>>()
        val bare = mutableSetOf<Pair<String, Int>>()
        val covered = body.parts.mapNotNullTo(hashSetOf()) { it.covering?.part }
        for (part in body.parts.filter(::measured)) {
            val id = requireNotNull(part.id)
            val mesh = part.surface.mesh
            val hidden = hiddenOf(part).toSet()
            val skin = part.covering == null && id in covered
            val used = if (skin) BooleanArray(mesh.vertexCount).also { flags -> mesh.indices.forEach { flags[it] = true } } else null
            for (v in 0 until mesh.vertexCount) {
                val wasHidden = (id.value to v) in hiddenAtRest
                val bareSkin = used?.get(v) == true
                if (v !in hidden && !wasHidden && !bareSkin) continue
                val p = part.origin + mesh.position(v)
                val seen = grid.seen(p)
                if (v in hidden && seen) holes += id.value to v
                if (v !in hidden && wasHidden && seen) shown += id.value to v
                if (bareSkin && seen) bare += id.value to v
            }
        }
        return Gaps(holes, shown, bare)
    }
}
