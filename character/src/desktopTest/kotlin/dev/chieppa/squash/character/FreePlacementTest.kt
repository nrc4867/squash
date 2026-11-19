package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.ViewParams
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CLOSE = 1e-3f
private const val OUTSET = 0.05f

/** Roll, Top's midline, stand-offs and rows round a part, through the real build and assembly. See HLD: data-model, Placing parts. */
class FreePlacementTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val planRef = BodyPlanRef(BodyPlanId("blank"), 1)
    private fun ref(id: String) = TemplateRef(TemplateId(id), 1)
    private val tube = library.latest(TemplateId("tube"))!!
    private val body = PartInstance(PartId("body"), ref("blob"))
    private val bead = PartInstance(PartId("bead"), ref("blob"), SocketRef(body.id, HandSockets.name(1)))

    private fun close(expected: Vec3, actual: Vec3, what: String) =
        assertTrue((expected - actual).length < CLOSE, "$what: expected $expected, got $actual")

    @Test
    fun rollTurnsTheUpTowardTheSide() {
        val rest = PartBuilder.axes(tube)
        val rolled = PartBuilder.axes(tube.placedBy(mapOf(PlacementParams.ROLL to 90f)))
        close(rest.along, rolled.along, "the axis stays")
        close(rest.side, rolled.up, "the up turns to where the side was")
        val half = PartBuilder.axes(tube.placedBy(mapOf(PlacementParams.ROLL to 180f)))
        close(rest.up * -1f, half.up, "half a turn turns it over")
    }

    @Test
    fun rollFollowsLeanAndOutward() {
        val turned = mapOf(PlacementParams.OUTWARD to 70f, PlacementParams.LEAN to 20f)
        val before = PartBuilder.axes(tube.placedBy(turned))
        val after = PartBuilder.axes(tube.placedBy(turned + (PlacementParams.ROLL to -90f)))
        close(before.along, after.along, "roll leaves where the part points")
        close(before.side * -1f, after.up, "a negative roll turns the up away from the side")
    }

    @Test
    fun aRowRoundThePartClosesOnAWholeTurn() {
        val params = HandSockets.addRow(body.id, 1, 0.5f, 0.5f, 0.1f, 0f, false, 4, 1f, 1f).values.filterValues { it != null }.mapValues { it.value!! } +
            (HandSockets.toV(1) to 1.1f)
        val specs = HandSockets.specs(params)
        assertEquals(listOf(0.1f, 0.35f, 0.6f, 0.85f), specs.map { (it.v * 100).roundToInt() / 100f })
        assertTrue(specs.all { it.u == 0.5f }, "the row stays at its u")
        val half = HandSockets.specs(params + (HandSockets.toV(1) to 0.4f))
        assertEquals(0.4f, half.last().v, CLOSE, "a row short of a turn ends at its toV")
    }

    private fun assembled(document: SquashDocument): AssembledCharacter {
        val character = document.character
        return assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(planRef), library)
    }

    private fun SquashDocument.with(command: DocumentCommand) = command.apply(this).document

    @Test
    fun anOutsetStandsAPartOffTheSurfaceAlongItsNormal() {
        val empty = SquashDocument(Character(CharacterId("c"), "Bead", planRef))
        val socketed = AddParts(listOf(body)).apply(empty).document.with(HandSockets.add(body.id, 1, 0.5f, 0f, 0f, false, sink = 0f))
        val before = assembled(AddParts(listOf(bead)).apply(socketed).document)
        val stood = socketed.with(SetParameters(body.id, mapOf(HandSockets.outset(1) to OUTSET)))
        val after = assembled(AddParts(listOf(bead)).apply(stood).document)
        val parent = assertNotNull(before.part(body.id))
        val normal = assertNotNull(parent.normalAt(0.5f, 0f))
        close(assertNotNull(before.part(bead.id)).origin + normal * OUTSET, assertNotNull(after.part(bead.id)).origin, "the bead stands off by its outset")
    }

    @Test
    fun topsMidlineMovesTheMiddleOnlyWhenAsked() {
        val part = PartInstance(PartId("band"), ref("tube"))
        val empty = SquashDocument(Character(CharacterId("c"), "Band", planRef))
        val curved = OutlineCurve(listOf(0f to 0.05f, 0.3f to 0.25f, 0.7f to 0.25f, 1f to 0.05f, 0.7f to 0.15f, 0.3f to 0.15f, 0f to -0.05f).map { (x, y) -> CurvePoint(x, y) })
        val drawn = AddParts(listOf(part)).apply(empty).document.with(SetOutline(part.id, View.TOP, curved))
        val plain = assertNotNull(assembled(drawn).part(part.id))
        val moved = assertNotNull(assembled(drawn.with(SetParameters(part.id, mapOf(ViewParams.TOP_MIDDLE to 1f)))).part(part.id))
        val axes = PartBuilder.axes(plain.template)
        val sideAtMiddle = { p: dev.chieppa.squash.character.build.PlacedPart -> assertNotNull(p.middleAt(0.5f)) dot axes.side }
        assertTrue(abs(sideAtMiddle(plain)) < CLOSE, "Top sets width alone by default")
        assertTrue(sideAtMiddle(moved) < -0.1f, "Top's midline, up its y, moves the middle the other way from the side")
    }
}
