package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.chips
import dev.chieppa.squash.character.catalog.firstTaken
import dev.chieppa.squash.character.catalog.isUpright
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.partCount
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.BodyPlanLint
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val UPRIGHT_REST = 0.99f
private const val ON_THE_GROUND = 1e-4f

/** The biped plan: an upright torso with a round head, arms with hands and legs with feet, a person's body before anything is drawn. */
class BipedPlanTest {
    private val plan = shipped.plans.single { it.id.value == "biped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    @Test
    fun thePlanLintsCleanAndItsRootStandsUpright() {
        assertEquals(emptyList(), BodyPlanLint.lint(plan, shipped.parts))
        assertTrue(plan.isUpright())
        val root = template(plan.root.template.value)
        assertEquals(root.axis.rest, plan.root.rest, "the plan's root rests as its template does")
        assertTrue(root.axis.rest.toVec3().normalized() dot Vec3.Y >= UPRIGHT_REST)
        listOf("arm", "hand", "foot", "head-round", "torso-upright").forEach { assertEquals(emptyList(), TemplateLint.lint(template(it)), it) }
    }

    @Test
    fun newCharacterGivesTheTorsoWithItsSocketsAndThePlannedBody() {
        val made = newCharacter(plan, shipped, "Person", ids())
        val document = made.commands.fold(made.document) { document, command -> command.apply(document).document }
        val torso = document.character.parts.single()
        assertEquals("torso-upright", torso.template.id.value)
        assertEquals(Role.BODY, torso.role)
        val root = template("torso-upright")
        assertEquals(listOf("neck", "shoulder", "hip"), socketsOn(plan, root, emptyMap()).map { it.name })
        assertEquals("head-round", assertNotNull(shipped.firstTaken(socketsOn(plan, root, emptyMap()).first())).id.value)

        val assembled = assembleNow(assemblyParts(document.character), { document.character.resolved(it) ?: PartData() }, plan, library)
        val planned = assembled.parts.filter { it.planned }.groupingBy { it.template.id.value }.eachCount()
        assertEquals(mapOf("head-round" to 1, "arm" to 2, "hand" to 2, "leg" to 2, "foot" to 2), planned)
        assertTrue(shipped.roles(document.character).upright)
        assertEquals(mapOf("Torso" to 1, "Round head" to 1, "Arm" to 2, "Leg" to 2), plan.chips(shipped).associate { it.name to it.count })
        assertEquals(6, plan.partCount(shipped))
    }

    @Test
    fun theFloorRuleStandsThePlannedFeetOnTheGround() {
        val made = newCharacter(plan, shipped, "Person", ids())
        val document = made.commands.fold(made.document) { document, command -> command.apply(document).document }
        val assembled = assembleNow(assemblyParts(document.character), { document.character.resolved(it) ?: PartData() }, plan, library)
        val lowest = assembled.parts.minBy { assertNotNull(it.box).low.y }
        assertEquals("foot", lowest.template.id.value)
        assembled.parts.filter { it.template.id.value == "foot" }.forEach { assertEquals(assembled.floor, assertNotNull(it.box).low.y, ON_THE_GROUND) }
        assembled.parts.filter { it.template.id.value == "hand" }.forEach { assertTrue(assertNotNull(it.box).low.y > assembled.floor, "a hand hangs clear of the ground") }
    }

    @Test
    fun theFootEndsALegThatBearsWeightAndTheHandAnArmThatDoesNot() {
        val leg = template("leg")
        val arm = template("arm")
        assertTrue(leg.axis.bearsWeight)
        assertEquals("foot", assertNotNull(shipped.firstTaken(socketsOn(plan, leg, emptyMap()).single { it.name == "paw" })).id.value)
        assertTrue(!arm.axis.bearsWeight)
        assertEquals(listOf("shoulder", "elbow", "wrist"), arm.blocks.flatMap { it.joints() }.map { it.name })
        assertTrue(arm.blocks.flatMap { it.joints() }.single { it.name == "wrist" }.stretch)
        assertEquals(listOf("hand"), arm.blocks.flatMap { it.sockets() }.single().takes.map { it.value })
    }

    @Test
    fun theThumbOverrideMovesOneDigitAlone() {
        val hand = template("hand")
        val unmoved = hand.copy(blocks = hand.blocks.map { if (it is ToeRow) it.copy(digits = emptyList()) else it })
        val with = PartBuilder.build(hand).associateBy { it.name }
        val without = PartBuilder.build(unmoved).associateBy { it.name }
        assertEquals(with.keys, without.keys)
        assertEquals(5, with.keys.count { it.startsWith("fingers:") })
        val moved = with.keys.filter { !with.getValue(it).points.contentEquals(without.getValue(it).points) }
        assertEquals(listOf("fingers:0"), moved)
    }

    private fun ids(): () -> String {
        var next = 0
        return { "id-${next++}" }
    }
}
