package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.ROOT_BLEND_SHARE
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.character.surface.openRootRadius
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Joins round over as wide as the joining part is thick, a socket at a closed end sits on the part's axis, and the foot and paw blend
 * into the leg by their templates' share of their length.
 */
class RoundJoinsTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1))
    private val leg = PartInstance(PartId("leg"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"))
    private val paw = PartInstance(PartId("paw"), TemplateRef(TemplateId("paw"), 9), SocketRef(leg.id, "paw"))

    private val built: AssembledCharacter by lazy {
        val character = AddParts(listOf(torso, leg, paw)).apply(SquashDocument(Character(CharacterId("c"), "Fennec", plan))).document.character
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(plan), library)
    }
    private val field: CharacterField by lazy { assertNotNull(fieldOf(built, RunSolids())) }

    private fun first(part: PartInstance) = field.runs.indexOfFirst { it.partId == part.id && it.first }

    @Test
    fun aLimbRoundsIntoItsParentByItsRootRadius() {
        val run = first(leg)
        val root = openRootRadius(field.runs[run].patch)
        assertTrue(root > 0f, "a leg starts open")
        assertEquals(ROOT_BLEND_SHARE * root, field.field.members[run].blend, 1e-4f)
    }

    @Test
    fun aLimbBlendsByTheAngleItMeetsItsParentAtAndATemplateJoinDoesNot() {
        assertNotNull(field.field.members[first(leg)].angles, "a leg's join blends by angle")
        assertNull(field.field.members[first(paw)].angles, "a paw keeps its template's join")
    }

    @Test
    fun aPawBlendsIntoTheLegByItsTemplatesShare() {
        val run = first(paw)
        val slices = assertNotNull(field.runs[run].patch.slices)
        val length = slices.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum()
        val share = assertNotNull(shipped.parts.single { it.id.value == "paw" && it.version == 9 }.join.blend)
        assertEquals(share * length, field.field.members[run].blend, 1e-4f)
        assertEquals(0f, openRootRadius(field.runs[run].patch), "a paw closes round its heel")
    }

    @Test
    fun aClosedEndIsAPoleOnTheAxis() {
        val part = assertNotNull(built.part(torso.id))
        assertNull(part.poleAt(0.5f), "the middle of a part is no pole")
        val pole = assertNotNull(part.poleAt(1f))
        val near = assertNotNull(part.middleAt(0.92f))
        val far = assertNotNull(part.middleAt(0.84f))
        val axis = (near - far).normalized()
        val off = (pole.point - near) - axis * ((pole.point - near) dot axis)
        assertTrue(off.length < 1e-4f, "on the axis, ${off.length} off it")
        assertTrue((pole.out dot axis) > 0.999f, "facing out along it")
    }

    @Test
    fun aParameterTimesAFactor() {
        val values = mapOf("ankle" to 0.2f)
        assertEquals(-0.1f, Param("\$ankle*-0.5").resolve(values), 1e-6f)
        assertEquals("ankle", Param("\$ankle*1.2").refName)
        assertEquals(0.2f, Param("\$ankle").resolve(values), 1e-6f)
    }
}
