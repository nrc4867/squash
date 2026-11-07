package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.asBuilt
import dev.chieppa.squash.character.build.shapedBy
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.lint.BodyPlanLint
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.role.PartRoles
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val GENERIC_IDS = listOf("tube", "blob", "sheet")
private val BODY = PartInstance(PartId("body"), templateRef("tube"), role = Role.BODY)
private const val TOP_SOCKET = 1
private const val REAR_SOCKET = 2
private const val SIDE_SOCKET = 3

class GenericTemplatesTest {
    private val blank = shipped.plans.first { it.id.value == "blank" }

    @Test
    fun eachGenericTemplateLintsBuildsWatertightAndGivesNoRole() {
        for (id in GENERIC_IDS) {
            val template = template(id)
            assertEquals(emptyList(), TemplateLint.lint(template), id)
            assertEquals(null, template.role, id)
            assertEquals(1, template.blocks.sumOf { it.joints().size }, "$id has one bone end to end")
            val mesh = PartBuilder.mesh(id, template, PartBuilder.build(template))
            mesh.patches.forEach { assertEquals(emptyList(), MeshInvariants.check(it, PartBuilder.openRings(template, it.patch)), id) }
        }
    }

    @Test
    fun aSheetIsOpenInVWithTwoFacesMeetingAtAnEdge() {
        val sheet = template("sheet")
        assertTrue(sheet.isSheet)
        val patch = PartBuilder.build(sheet).single()
        assertFalse(patch.closedV)
        assertEquals(sheet.topology.segments + 1, patch.nv)
        assertTrue(patch.meetsAround, "v = 0 and v = 1 lie on one edge")
        val ring = patch.nu / 2
        val upper = patch.point(patch.index(ring, patch.segments / 4))
        val lower = patch.point(patch.index(ring, 3 * patch.segments / 4))
        val centre = assertNotNull(patch.slices)[ring].frame.center
        assertTrue(((upper - centre) dot (lower - centre)) < 0f, "the two faces lie either side of the middle")
    }

    @Test
    fun aBlobIsASphereWithAPoleAtEachEnd() {
        val blob = template("blob")
        val patch = PartBuilder.build(blob).single()
        assertTrue(patch.isPole(0) && patch.isPole(patch.nu - 1))
        val slices = assertNotNull(patch.slices)
        val middle = slices[slices.size / 2]
        assertEquals(middle.height, middle.width, 1e-4f)
        assertEquals(blob.axis.length, (patch.point(patch.index(patch.nu - 1, 0)) - patch.point(0)).length, 1e-3f)
    }

    @Test
    fun aGenericPartsChoicesShapeItsBuildWithinItsLimits() {
        val tube = template("tube")
        val shaped = tube.shapedBy(
            mapOf(GenericParams.SECTION to 3f, GenericParams.RINGS to 40f, GenericParams.SEGMENTS to 200f, GenericParams.view(View.FRONT) to 0f),
        )
        assertEquals(SectionSpec.Named(SectionProfile.LENS), shaped.section)
        assertEquals(40, shaped.topology.rings)
        assertEquals(tube.generic?.segments?.max, shaped.topology.segments)
        assertEquals(setOf(View.SIDE, View.TOP), shaped.views.keys)
        val patch = PartBuilder.build(tube, mapOf(GenericParams.RINGS to 40f)).single()
        assertEquals(40, patch.nu)
        val none = tube.shapedBy(GenericParams.READINGS.keys.associate { GenericParams.view(it) to 0f })
        assertEquals(tube.views, none.views, "the last view stays on")
        assertEquals(template("leg"), template("leg").shapedBy(mapOf(GenericParams.RINGS to 40f)), "a typed template keeps its own")
    }

    @Test
    fun aGenericRootHasNoSocketPlaneSoItsOpenEndCloses() {
        val root = template("tube").asBuilt(emptyMap(), onSocket = false)
        assertEquals(null, root.openEnd)
        val patch = PartBuilder.build(root).single()
        assertTrue(patch.isPole(0) && patch.isPole(patch.nu - 1))
        assertEquals(template("leg"), template("leg").asBuilt(emptyMap(), onSocket = false), "a typed root keeps its open end")
    }

    @Test
    fun theBlankPlanIsOneBodyTubeWithNoSockets() {
        assertEquals(emptyList(), BodyPlanLint.lint(blank, shipped.parts))
        val new = newCharacter(blank, shipped, "Koi") { "id" }
        val document = new.commands.fold(new.document) { doc, command -> command.apply(doc).document }
        val body = document.character.parts.single()
        assertEquals(TemplateId("tube"), body.template.id)
        assertEquals(Role.BODY, body.role)
        assertFalse(body.roleGuessed)
        assertEquals(null, body.socket)
        assertEquals("Body", partName(body, shipped))
        assertEquals(emptyList(), openSockets(document, blank, shipped))
    }

    @Test
    fun aGenericPartOnAHandMadeSocketHasItsRoleGuessed() {
        var document = blankDocument()
        document = HandSockets.add(BODY.id, TOP_SOCKET, 0.5f, 0.02f, 0f, paired = false).apply(document).document
        document = HandSockets.add(BODY.id, REAR_SOCKET, 0.02f, 0.05f, 0f, paired = false).apply(document).document
        document = HandSockets.add(BODY.id, SIDE_SOCKET, 0.5f, 0.25f, 0f, paired = false).apply(document).document
        val roles = PartRoles(document.character, { ref -> shipped.parts.firstOrNull { it.ref == ref } }, blank)
        fun guess(template: String, socket: Int) = roles.placed(template(template), SocketRef(BODY.id, HandSockets.name(socket)))
        assertEquals(Role.FIN, guess("sheet", TOP_SOCKET))
        assertEquals(Role.FIN, guess("sheet", SIDE_SOCKET))
        assertEquals(Role.TAIL, guess("sheet", REAR_SOCKET))
        assertEquals(Role.OTHER, guess("blob", SIDE_SOCKET))
        assertEquals(Role.OTHER, guess("tube", TOP_SOCKET))
    }

    @Test
    fun aSheetTakesAnchorsALineWeightAndARegion() = runTest {
        val sheet = PartInstance(PartId("fin"), templateRef("sheet"), SocketRef(BODY.id, HandSockets.name(TOP_SOCKET)), role = Role.FIN)
        val session = blankSession(HandSockets.add(BODY.id, TOP_SOCKET, 0.5f, 0.02f, 0f, paired = false), AddParts(listOf(sheet)))
        val border = listOf(0.2f, 0.4f, 0.6f, 0.8f).map { Anchor(sheet.id, BODY_PATCH, 0.5f, it) } + Anchor(sheet.id, BODY_PATCH, 0.8f, 0.5f)
        session.send(SetRegion(sheet.id, RegionId("tip"), Region(RegionId("tip"), border, SlotName("cream"))))
        session.send(SetLineWeight(sheet.id, LineWeightId("edge"), LineWeight(LineWeightId("edge"), listOf(Anchor(sheet.id, BODY_PATCH, 0.6f, 0f)), 0.4f, 1f)))
        session.settle()
        val patch = session.builtSurface(sheet).body()
        val work = assertNotNull(session.surfaceWork(sheet.id).output.value)
        assertEquals(border.map { patch.positionAt(it.u, it.v) }, work.regions.single().border.map { it.position })
        val lines = assertNotNull(session.bodyLines.output.value).parts.getValue(sheet.id)
        val contour = lines.grids.flatMap { grid -> grid.contours { Vec3(0f, 0f, -1f) } }.flatMap { it.points }
        assertTrue(contour.isNotEmpty(), "the sheet's edge turns edge on from the side")
        assertTrue(contour.maxOf { it.weight } > 1.5f, "the weight reaches the sheet's line")
    }

    @Test
    fun aBlobTakesAMarkAndChangingRingsRebuildsKeepingTheOutlines() = runTest {
        val blob = PartInstance(PartId("eye"), templateRef("blob"), SocketRef(BODY.id, HandSockets.name(SIDE_SOCKET)), role = Role.OTHER)
        val session = blankSession(HandSockets.add(BODY.id, SIDE_SOCKET, 0.8f, 0.3f, 0f, paired = false), AddParts(listOf(blob)))
        val points = listOf(Anchor(blob.id, BODY_PATCH, 0.4f, 0.2f), Anchor(blob.id, BODY_PATCH, 0.6f, 0.3f))
        session.send(SetMark(blob.id, MarkId("pupil"), Mark(MarkId("pupil"), points)))
        session.settle()
        val patch = session.builtSurface(blob).body()
        assertEquals(points.map { patch.positionAt(it.u, it.v) }, assertNotNull(session.surfaceWork(blob.id).output.value).marks.single().points.map { it.position })
        val outline = SetOutlines(BODY.id, mapOf(View.SIDE to outline(0f to 0.12f, 0.6f to 0.1f, 0.6f to -0.1f, 0f to -0.12f)))
        session.send(outline)
        session.send(GenericParams.rings(BODY.id, 48))
        session.settle()
        assertEquals(48, session.builtSurface(BODY).body().nu)
        assertEquals(outline.outlines, session.document.value.character.resolved(session.document.value.character.parts.first()).outlines)
    }

    private fun blankDocument(): SquashDocument =
        AddParts(listOf(BODY)).apply(SquashDocument(Character(CharacterId("koi"), "Koi", BodyPlanRef(BodyPlanId("blank"), 1)))).document

    private fun TestScope.blankSession(vararg commands: DocumentCommand): CharacterSession =
        CharacterSession(
            commands.fold(blankDocument()) { doc, command -> command.apply(doc).document },
            TemplateLibrary(shipped.parts, shipped.plans),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
}
