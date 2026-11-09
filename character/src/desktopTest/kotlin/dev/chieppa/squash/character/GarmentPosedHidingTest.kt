package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.character.build.hiddenAgain
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val EASE = 0.03f

/** What a garment hides is decided again in a pose: skin a pose carries out of a garment shows, and skin it carries into one is hidden. */
class GarmentPosedHidingTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val sweater = GarmentId("sweater")
    private val branch = branchPartId(sweater, torso)

    private val body: AssembledCharacter by lazy {
        assembleNow(listOf(AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null)), { PartData() }, plan, library)
    }

    private val dressed: AssembledCharacter by lazy {
        body.dressedIn(Dressing(listOf(Garment(sweater, "Sweater", branches = listOf(GarmentBranch(torso, 0.3f, 0.7f, EASE))))))
    }

    /** [character] with [part]'s mesh moved by [by], as a pose would show it. */
    private fun moved(character: AssembledCharacter, part: PartId, by: Vec3): AssembledCharacter {
        val mesh = assertNotNull(character.part(part)).surface.mesh
        val vertices = mesh.vertices.copyOf()
        for (v in 0 until mesh.vertexCount) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            vertices[at] += by.x
            vertices[at + 1] += by.y
            vertices[at + 2] += by.z
        }
        val pose = object : PosedParts {
            override val meshes = mapOf(part to RenderMesh(vertices, mesh.indices))
            override val turns = emptyMap<PartId, Mat3>()
        }
        return character.posedBy(pose).hiddenAgain(character)
    }

    private fun shownFaces(character: AssembledCharacter, part: PartId) = assertNotNull(character.part(part)).surface.mesh.indices.size / 3

    private fun torsoLength(): Float = assertNotNull(body.part(torso)).box!!.size.let { maxOf(it.x, it.y, it.z) }

    @Test
    fun skinAPoseCarriesOutOfEveryGarmentShowsAgain() {
        val bare = shownFaces(body, torso)
        assertTrue(shownFaces(dressed, torso) < bare, "the sweater hides skin at rest")
        val away = moved(dressed, branch, Vec3(0f, torsoLength() * 3f, 0f))
        assertEquals(bare, shownFaces(away, torso), "nothing is under the sweater once it is carried off the torso")
    }

    @Test
    fun skinAPoseCarriesIntoAGarmentIsHidden() {
        val restHidden = hiddenOf(dressed)
        val along = assertNotNull(dressed.part(branch)).box!!.size.x * 0.25f
        val slid = moved(dressed, branch, Vec3(along, 0f, 0f))
        val nowHidden = hiddenOf(slid)
        assertTrue((nowHidden - restHidden).isNotEmpty(), "skin the sweater now covers is hidden")
        assertTrue((restHidden - nowHidden).isNotEmpty(), "skin the sweater left shows")
    }

    @Test
    fun aPoseThatMovesNothingUnderAGarmentLeavesItsPartsAlone() {
        val tail = moved(dressed, branch, Vec3.ZERO)
        assertSame(dressed.part(torso), tail.part(torso))
    }

    /** The torso's vertices no shown face has as a corner. */
    private fun hiddenOf(character: AssembledCharacter): Set<Int> {
        val mesh = assertNotNull(character.part(torso)).surface.mesh
        val used = BooleanArray(mesh.vertexCount).also { flags -> mesh.indices.forEach { flags[it] = true } }
        return used.indices.filter { !used[it] }.toSet()
    }
}
