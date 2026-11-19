package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.partGrids
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.character.surface.isFieldPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SetShades
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.ringMiddle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SPINES = 40
private const val LINKS = 9
private val COPIES = PartId("copies")

/** Copies of one part shown by one socket: scattered over an area by a seed, or repeated along a path, each an instance of one build. */
class CopiesTest {
    private val plan = shipped.plans.first()
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    private fun built(template: String, data: PartData = PartData(), socket: (PartId) -> DocumentCommand): Pair<AssembledCharacter, PartId> {
        val new = newCharacter(plan, shipped, "Copies")
        var document: SquashDocument = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        document = socket(new.root).apply(document).document
        val ref = assertNotNull(shipped.template(TemplateId(template))).ref
        document = AddParts(listOf(PartInstance(COPIES, ref, SocketRef(new.root, HandSockets.name(1))))).apply(document).document
        if (data.params.isNotEmpty()) document = SetParameters(COPIES, data.params).apply(document).document
        if (data.surface.shades.isNotEmpty()) document = SetShades(COPIES, data.surface.shades).apply(document).document
        val character = document.character
        return assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library) to new.root
    }

    private fun spread(root: PartId, seed: Int, spacing: Float = 0f) =
        HandSockets.addSpread(root, 1, 0.2f, 0.8f, 0f, 1f, SPINES, seed, spacing, sizeJitter = 0.2f, leanJitter = 10f, turnJitter = 90f)

    private fun spreadOn(seed: Int, spacing: Float = 0f): Pair<AssembledCharacter, PartId> = built("spike") { spread(it, seed, spacing) }

    @Test
    fun aSpreadShowsItsCountOfCopiesOfOneBuildAndLeavesTheField() {
        val (character, _) = spreadOn(3)
        val part = assertNotNull(character.part(COPIES))
        val copies = assertNotNull(part.copies)
        assertEquals(SPINES, copies.places.size)
        assertEquals(SPINES, copies.places.map { it.origin }.toSet().size)
        assertTrue(!isFieldPart(part))
        assertEquals(SPINES * copies.rule.surface.patches.size, partGrids(part).size)
    }

    @Test
    fun copiesOfACurvedPartAllCarryItsCurve() {
        val hook = AxisCurveParams.of(listOf(Vec3(0.4f, 0f, 0f), Vec3(0.7f, 0.25f, 0f), Vec3(0.55f, 0.5f, 0.1f)))
        val (character, _) = built("spike", PartData(params = hook)) { spread(it, 3) }
        val copies = assertNotNull(character.part(COPIES)?.copies)
        assertEquals(SPINES, copies.places.size)
        val patch = copies.rule.surface.patches.first()
        val spike = assertNotNull(shipped.template(TemplateId("spike")))
        assertTrue(patch.nu > spike.topology.rings, "the one build the copies share is the curved one, ${patch.nu} rings")
        val tip = patch.ringMiddle(1f)
        assertTrue((tip - patch.ringMiddle(0f)).length < spike.axis.length * 0.8f, "its tip hooks back toward its root")
    }

    @Test
    fun theSameSeedPicksTheSamePlacesAndAnotherSeedOthers() {
        val first = assertNotNull(spreadOn(3).first.part(COPIES)?.copies).places.map { it.origin }
        val again = assertNotNull(spreadOn(3).first.part(COPIES)?.copies).places.map { it.origin }
        val other = assertNotNull(spreadOn(4).first.part(COPIES)?.copies).places.map { it.origin }
        assertEquals(first, again)
        assertNotEquals(first, other)
    }

    @Test
    fun aWholeTurnWrapsRoundThePartToBothSides() {
        val places = assertNotNull(spreadOn(5).first.part(COPIES)?.copies).places
        assertTrue(places.any { it.origin.z > 0.02f } && places.any { it.origin.z < -0.02f }, "copies on both sides: ${places.map { it.origin.z }}")
    }

    @Test
    fun spacingKeepsCopiesApartAndShowsFewerWhereNoMoreFit() {
        val spacing = 0.15f
        val places = assertNotNull(spreadOn(6, spacing).first.part(COPIES)?.copies).places
        assertTrue(places.size in 1 until SPINES, "${places.size} copies fit")
        val nearest = places.indices.minOf { i -> places.indices.filter { it != i }.minOf { j -> (places[i].origin - places[j].origin).length } }
        assertTrue(nearest > spacing * 0.6f, "nearest two stand $nearest apart")
    }

    @Test
    fun aRowSocketWithoutCopiesShowsNone() {
        val (character, _) = built("spike") { HandSockets.add(it, 1, 0.5f, 0.5f, 0f, false) }
        assertNull(assertNotNull(character.part(COPIES)).copies)
    }

    @Test
    fun copiesPickAmongTheirShades() {
        val shades = listOf(SlotName("light"), SlotName("dark"))
        val (character, _) = built("spike", PartData(surface = SurfaceWork(shades = shades))) { spread(it, 7) }
        val slots = assertNotNull(character.part(COPIES)?.copies).places.map { it.slot }.toSet()
        assertEquals(setOf(null) + shades, slots)
    }

    @Test
    fun aPathRepeatsItsCountEvenlyAlongItAndRollsEveryOther() {
        val (character, _) = built("ring") { HandSockets.addPath(it, 1, 0.3f, 0.25f, 0.7f, 0.25f, LINKS, roll = 90f) }
        val places = assertNotNull(character.part(COPIES)?.copies).places
        assertEquals(LINKS, places.size)
        val steps = places.zipWithNext { a, b -> (b.origin - a.origin).length }
        assertTrue(steps.all { it > 0f })
        val holes = places.map { it.turn.z.normalized() }
        holes.zipWithNext().forEach { (a, b) -> assertTrue(abs(a dot b) < 0.2f, "each link turns square to the last: ${a dot b}") }
    }

    @Test
    fun aRingsLinksStepByTheirInsideLengthSoEachHooksTheNext() {
        val (character, _) = built("ring", PartData(params = mapOf("stretch" to 1.6f))) { HandSockets.addPath(it, 1, 0.3f, 0.25f, 0.7f, 0.25f, 0, roll = 90f) }
        val copies = assertNotNull(character.part(COPIES)?.copies)
        val mesh = copies.rule.surface.mesh
        val template = assertNotNull(shipped.template(TemplateId("ring")))
        val axes = PartBuilder.axes(template)
        val up = (0 until mesh.vertexCount).map { mesh.position(it) dot axes.up }
        val hole = (0 until mesh.vertexCount).map { mesh.position(it) dot axes.along }
        val inside = (up.max() - up.min()) - 2f * (hole.max() - hole.min())
        val steps = copies.places.zipWithNext { a, b -> (b.origin - a.origin).length }
        assertTrue(copies.places.size > 2)
        assertTrue(steps.all { abs(it - inside) < inside * 0.25f }, "links step by $inside: $steps")
        assertTrue(steps.all { it < up.max() - up.min() }, "links overlap end to end")
    }

    @Test
    fun aRingIsAClosedLoopItsLastRingOnItsFirst() {
        val template = assertNotNull(shipped.template(TemplateId("ring")))
        val patch = PartBuilder.build(template).single()
        val first = (0 until patch.nv).map { patch.point(patch.index(0, it)) }
        val last = (0 until patch.nv).map { patch.point(patch.index(patch.nu - 1, it)) }
        first.zip(last).forEach { (a, b) -> assertTrue((a - b).length < 1e-5f) }
        val middle = (0 until patch.nv).map { patch.point(patch.index(patch.nu / 2, it)) }
        assertTrue(middle.zip(first).all { (a, b) -> (a - b).length > 0.01f })
    }

    @Test
    fun aSpreadAsksNoMoreThanTheMostCopies() {
        val params = HandSockets.addSpread(PartId("body"), 1, 0f, 1f, 0f, 1f, CopySpec.MAX_COPIES * 2, 1).values.filterValues { it != null }.mapValues { it.value!! }
        assertEquals(CopySpec.MAX_COPIES, assertNotNull(HandSockets.copiesOf(params, 1)).count)
    }
}
