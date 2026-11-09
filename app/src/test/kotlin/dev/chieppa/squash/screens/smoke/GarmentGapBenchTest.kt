package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.isBranchPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.RigPart
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

/**
 * What a pose leaves showing under the dressed fennec's clothes: of the vertices hidden at rest that lie inside a garment then, on the
 * body and on inner layers, how many a pose carries outside every garment's surface, where they leave a hole. Read on the posed meshes by the
 * nearest garment vertex's normal. Numbers go to the test's output.
 */
@Tag("slow")
class GarmentGapBenchTest {
    private val work = TestWork()

    private val poses = listOf(
        "rest" to Pose(),
        "knee 60" to Pose(listOf(BonePose(FRONT_LEG_L, "knee", 60f))),
        "knee 110" to Pose(listOf(BonePose(FRONT_LEG_L, "knee", 110f))),
        "neck 20" to Pose(listOf(BonePose(HEAD, "neck", 20f))),
        "neck 40" to Pose(listOf(BonePose(HEAD, "neck", 40f))),
    )

    @Test
    fun theDressedFennec() {
        val session = CharacterSession(fennecDressedDocument(), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        val pose = SessionPose(session)
        work.settle(session)
        val rig = assertNotNull(pose.rig.rig.output.value)
        var under: Set<Pair<String, Int>>? = null
        for ((name, set) in poses) {
            session.send(SetPose(set))
            work.settle(session)
            val posed = assertNotNull(pose.posed.output.value).parts
            val garments = rig.parts.filter { isBranchPart(it.id) }
            val surface = garments.flatMap { part -> surfacePoints(part, posed.getValue(part.id)) }
            val inside = mutableSetOf<Pair<String, Int>>()
            var open = 0
            val byPart = mutableMapOf<String, Int>()
            for (part in rig.parts) {
                val positions = posed[part.id] ?: continue
                val used = BooleanArray(part.vertexCount).also { flags -> part.indices.forEach { flags[it] = true } }
                val others = if (isBranchPart(part.id)) surface.filter { it.part != part.id.value } else surface
                for (v in 0 until part.vertexCount) {
                    if (used[v] || under?.contains(part.id.value to v) == false) continue
                    val p = at(positions, v)
                    val near = others.minByOrNull { (it.position - p).length } ?: continue
                    if ((p - near.position) dot near.normal <= 0f) {
                        inside += part.id.value to v
                    } else {
                        open++
                        byPart.merge(part.id.value, 1, Int::plus)
                    }
                }
            }
            if (under == null) {
                under = inside
                println("dressed fennec at rest: ${inside.size} hidden vertices inside a garment")
                continue
            }
            println("dressed fennec, $name: $open of ${under!!.size} vertices hidden inside a garment at rest now outside every garment; by part $byPart")
        }
    }

    private class SurfacePoint(val part: String, val position: Vec3, val normal: Vec3)

    private fun surfacePoints(part: RigPart, positions: FloatArray): List<SurfacePoint> {
        val normals = Array(part.vertexCount) { Vec3.ZERO }
        val indices = part.indices
        for (t in 0 until indices.size / 3) {
            val a = indices[t * 3]
            val b = indices[t * 3 + 1]
            val c = indices[t * 3 + 2]
            val face = (at(positions, b) - at(positions, a)) cross (at(positions, c) - at(positions, a))
            normals[a] = normals[a] + face
            normals[b] = normals[b] + face
            normals[c] = normals[c] + face
        }
        return (0 until part.vertexCount).filter { normals[it].length > 0f }.map { SurfacePoint(part.id.value, at(positions, it), normals[it].normalized()) }
    }

    private fun at(values: FloatArray, vertex: Int) = Vec3(values[vertex * 3], values[vertex * 3 + 1], values[vertex * 3 + 2])
}
