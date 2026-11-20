package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.membraneBetween
import dev.chieppa.squash.character.build.membraneColors
import dev.chieppa.squash.character.build.withMembranes
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.surface.isFieldPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Membrane
import dev.chieppa.squash.document.MembraneEdge
import dev.chieppa.squash.document.MembraneId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMembrane
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.membranePartId
import dev.chieppa.squash.document.mirroredMembraneId
import dev.chieppa.squash.document.shownMembranes
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A membrane spanned between two parts: a closed thin sheet of its own, from one rail to the other, mirrored, coloured and undone in one step. */
class MembraneSpanTest {
    private val plan = shipped.plans.first { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    /** A small quadruped with its four legs placed, and the left front and hind leg's ids. */
    private val legged: Triple<SquashDocument, PartId, PartId> by lazy {
        val new = newCharacter(plan, shipped, "Glider")
        var document = new.commands.fold(new.document) { doc, command -> command.apply(doc).document }
        val leg = assertNotNull(shipped.parts.filter { it.id == TemplateId("leg") }.maxByOrNull { it.version })
        for (socket in listOf("front-leg", "back-leg")) {
            val open = openSockets(document, plan, shipped).first { it.name == socket }
            document = AddParts(placeOnSocket(open, leg, document, roles = shipped.roles(document.character))).apply(document).document
        }
        fun left(socket: String) = document.character.parts.first { it.socket?.socket == socket && it.side == Side.LEFT }.id
        Triple(document, left("front-leg"), left("back-leg"))
    }

    private fun patagium(front: PartId, hind: PartId, mirrored: Boolean = false) = Membrane(
        MembraneId("patagium"),
        first = listOf(MembraneEdge(front, 0.1f, 0.25f, 0.9f, 0.25f)),
        second = listOf(MembraneEdge(hind, 0.1f, 0.25f, 0.9f, 0.25f)),
        mirrored = mirrored,
        color = SlotName("fur"),
    )

    private fun assemble(document: SquashDocument): AssembledCharacter =
        assembleNow(assemblyParts(document.character), { id -> document.character.resolved(assertNotNull(document.character.part(id))) }, plan, library)
            .withMembranes(document.character.shownMembranes)

    @Test
    fun aMembraneIsAClosedSheetOfItsOwnBetweenItsTwoRails() {
        val (document, front, hind) = legged
        val applied = SetMembrane(MembraneId("patagium"), patagium(front, hind)).apply(document)
        val built = assemble(applied.document)
        val sheet = assertNotNull(built.part(membranePartId(MembraneId("patagium"))))
        assertTrue(!isFieldPart(sheet), "a membrane is a mesh of its own")
        val mesh = sheet.surface.patches.single()
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(mesh) { 0 }, emptySet()))
        val frontLeg = assertNotNull(built.part(front))
        val hindLeg = assertNotNull(built.part(hind))
        val first = assertNotNull(frontLeg.middleAt(0.5f))
        val second = assertNotNull(hindLeg.middleAt(0.5f))
        val points = (0 until mesh.nu * mesh.nv).map { mesh.point(it) }
        val nearFront = points.minOf { (it - first).length }
        val nearHind = points.minOf { (it - second).length }
        val gap = (second - first).length
        assertTrue(nearFront < gap * 0.2f && nearHind < gap * 0.2f, "it reaches both legs: $nearFront and $nearHind of $gap")
        assertEquals(document, applied.undo.apply(applied.document).document)
    }

    @Test
    fun aMirroredMembraneIsBuiltOnTheOtherSideToo() {
        val (document, front, hind) = legged
        val placed = SetMembrane(MembraneId("patagium"), patagium(front, hind, mirrored = true)).apply(document).document
        val built = assemble(placed)
        val left = assertNotNull(built.part(membranePartId(MembraneId("patagium"))))
        val right = assertNotNull(built.part(membranePartId(mirroredMembraneId(MembraneId("patagium")))))
        fun middle(points: List<Vec3>) = points.fold(Vec3.ZERO) { a, b -> a + b } * (1f / points.size)
        val l = middle(left.shownPoints())
        val r = middle(right.shownPoints())
        assertEquals(l.x, r.x, 0.01f)
        assertEquals(l.z, -r.z, 0.01f)
        assertTrue(l.z * r.z < 0f, "the two lie either side of the body")
        assertEquals(setOf(membranePartId(MembraneId("patagium")), membranePartId(mirroredMembraneId(MembraneId("patagium")))), membraneColors(placed.character).keys)
        assertEquals(SlotName("fur"), membraneColors(placed.character).values.first().baseSlot)
    }

    @Test
    fun aMembraneWhosePartIsGoneBuildsNothing() {
        val (document, front, _) = legged
        val placed = SetMembrane(MembraneId("patagium"), patagium(front, front).copy(second = listOf(MembraneEdge(PartId("gone"), 0f, 0f, 1f, 0f)))).let { command ->
            document.copy(character = document.character.copy(membranes = listOf(assertNotNull(command.membrane))))
        }
        assertNull(assemble(placed).part(membranePartId(MembraneId("patagium"))))
    }

    @Test
    fun billowMovesItsMiddleOutFromTheFlankWhereItHangs() {
        val (document, front, hind) = legged
        fun furthest(billow: Float): Float {
            val placed = SetMembrane(MembraneId("patagium"), patagium(front, hind).copy(billow = billow)).apply(document).document
            return assertNotNull(assemble(placed).part(membranePartId(MembraneId("patagium")))).shownPoints().maxOf { kotlin.math.abs(it.z) }
        }
        assertTrue(furthest(0.3f) > furthest(-0.3f), "a sheet hanging beside the body billows out from it for more, in for less")
    }

    @Test
    fun aNewMembraneRunsAlongTheSidesOfItsPartsThatFaceEachOther() {
        val (document, front, hind) = legged
        val built = assemble(document)
        val membrane = assertNotNull(membraneBetween(MembraneId("new"), front, hind, built, mirrored = true))
        val frontLeg = assertNotNull(built.part(front))
        val hindLeg = assertNotNull(built.part(hind))
        val onFront = assertNotNull(frontLeg.pointOn(0.5f, membrane.first.single().fromV))
        val awayOnFront = assertNotNull(frontLeg.pointOn(0.5f, (membrane.first.single().fromV + 0.5f) % 1f))
        val hindMiddle = assertNotNull(hindLeg.middleAt(0.5f))
        assertTrue((onFront - hindMiddle).length < (awayOnFront - hindMiddle).length, "the first rail lies on the front leg's side toward the hind leg")
        assertNotNull(assemble(SetMembrane(membrane.id, membrane).apply(document).document).part(membranePartId(membrane.id)))
    }
}
