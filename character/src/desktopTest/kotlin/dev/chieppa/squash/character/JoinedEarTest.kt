package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.DETAIL_LEVEL
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.character.surface.digitLevels
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ear version 3 is a run of the head's field: one surface with the skull, joined by its template's wide blend, its join unlined unless the
 * part asks, and meshed as finely as the head. Versions 1 and 2 stay meshes of their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("integration")
class JoinedEarTest {
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head"), 2))
    private val pair = PairId("ears")

    private fun ears(version: Int) = listOf(Side.LEFT to "ear-l", Side.RIGHT to "ear-r").map { (side, id) ->
        PartInstance(PartId(id), TemplateRef(TemplateId("ear"), version), SocketRef(head.id, "ear"), pair = pair, side = side)
    }

    private fun CharacterSession.surface(): CharacterSurface = assertNotNull(characterSurface.output.value?.surface)

    @Test
    fun theEarIsARunOfTheHeadsFieldJoinedWideAndUnlined() = runTest {
        val session = surfaceSession(head, *ears(3).toTypedArray())
        session.settle()
        val surface = session.surface()
        assertEquals(setOf(head.id, PartId("ear-l"), PartId("ear-r")), surface.parts)
        val ear = surface.field.runs.indexOfFirst { it.partId == PartId("ear-l") }
        val member = surface.field.field.members[ear]
        val slices = assertNotNull(surface.field.runs[ear].patch.slices)
        val length = slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum()
        val share = assertNotNull(shipped.parts.single { it.id.value == "ear" && it.version == 3 }.join.blend)
        assertEquals(share * length, member.blend, 1e-4f, "the ear joins by its template's share of its length")
        assertEquals(DETAIL_LEVEL, digitLevels(surface.field)[ear])
        assertTrue(surface.joins.isEmpty(), "no line where the ear rises from the skull")

        session.send(SetParameter(PartId("ear-l"), JoinParams.INK, 1f))
        session.settle()
        assertFalse(session.surface().joins.isEmpty(), "a part that asks for its join line gets one")
    }

    @Test
    fun earlierVersionsStayMeshesOfTheirOwn() = runTest {
        val session = surfaceSession(head, *ears(2).toTypedArray())
        session.settle()
        assertEquals(setOf(head.id), session.surface().parts)
    }

    @Test
    fun aSmallEarJoinedIntoTheHeadBuildsClosedAtTheSmallestSize() = runTest {
        val session = surfaceSession(head, *ears(3).toTypedArray())
        for (size in listOf(0.2f, 0.15f)) {
            session.send(SetParameter(PartId("ear-l"), SIZE_PARAM, size))
            session.settle()
            val surface = session.surface()
            assertEquals(setOf(head.id, PartId("ear-l"), PartId("ear-r")), surface.parts, "size $size")
            val triangles = surface.mesh.triangles
            val directed = HashSet<Long>()
            for (corner in triangles.indices) {
                val triangle = corner / 3 * 3
                directed += triangles[corner].toLong() shl Int.SIZE_BITS or triangles[triangle + (corner - triangle + 1) % 3].toLong()
            }
            val open = directed.count { edge -> (edge.toInt().toLong() shl Int.SIZE_BITS or (edge ushr Int.SIZE_BITS)) !in directed }
            assertEquals(0, open, "open edges at ear size $size")
        }
    }
}
