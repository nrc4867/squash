package dev.chieppa.squash.character

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

private const val ON_THE_GROUND = 1e-4f

/** How far up the perched torso rests, as its rest's upward share: about 50 degrees, past the upright line. */
private const val PERCHED_UP = 0.7f
private const val PERCHED_FORWARD = 0.5f

private val BIRD_TEMPLATES = listOf("beak", "beak-lower", "foot-bird", "head-bird", "leg-bird", "tail-feathers", "torso-bird")

/** The bird plan: a perched torso with a bird head and beak, wings, two thin legs with bird feet and a tail, before anything is drawn. */
class BirdPlanTest {
    private val plan = shipped.plans.single { it.id.value == "bird" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    @Test
    fun thePlanLintsCleanAndItsRootPerches() {
        assertEquals(emptyList(), BodyPlanLint.lint(plan, shipped.parts))
        val root = template(plan.root.template.value)
        assertEquals(root.axis.rest, plan.root.rest, "the plan's root rests as its template does")
        val rest = root.axis.rest.toVec3().normalized()
        assertTrue(rest.y >= PERCHED_UP && rest.x >= PERCHED_FORWARD, "the torso leans forward from upright: $rest")
        assertTrue(plan.isUpright())
        BIRD_TEMPLATES.forEach { assertEquals(emptyList(), TemplateLint.lint(template(it)), it) }
    }

    @Test
    fun newCharacterListsTheBirdAfterTheQuadrupedAndGivesTheTorsoWithAWholePerchedBirdPlanned() {
        assertEquals(listOf("canine-quadruped", "bird", "biped", "blank"), shipped.plans.map { it.id.value })
        val made = newCharacter(plan, shipped, "Bird", ids())
        val document = made.commands.fold(made.document) { document, command -> command.apply(document).document }
        val torso = document.character.parts.single()
        assertEquals("torso-bird", torso.template.id.value)
        assertEquals(Role.BODY, torso.role)
        val root = template("torso-bird")
        assertEquals(listOf("neck", "shoulder", "hip", "tail"), socketsOn(plan, root, emptyMap()).map { it.name })
        assertEquals("head-bird", assertNotNull(shipped.firstTaken(socketsOn(plan, root, emptyMap()).first())).id.value)

        val assembled = assembleNow(assemblyParts(document.character), { document.character.resolved(it) ?: PartData() }, plan, library)
        val planned = assembled.parts.filter { it.planned }.groupingBy { it.template.id.value }.eachCount()
        assertEquals(
            mapOf("head-bird" to 1, "beak" to 1, "beak-lower" to 1, "wing-feathered" to 2, "leg-bird" to 2, "foot-bird" to 2, "tail-feathers" to 1),
            planned,
        )
        assertTrue(shipped.roles(document.character).upright)
        assertEquals(mapOf("Torso" to 1, "Bird head" to 1, "Wing" to 2, "Bird leg" to 2, "Tail feathers" to 1), plan.chips(shipped).associate { it.name to it.count })
        assertEquals(8, plan.partCount(shipped))
    }

    @Test
    fun theFloorRuleStandsThePlannedFeetOnTheGround() {
        val made = newCharacter(plan, shipped, "Bird", ids())
        val document = made.commands.fold(made.document) { document, command -> command.apply(document).document }
        val assembled = assembleNow(assemblyParts(document.character), { document.character.resolved(it) ?: PartData() }, plan, library)
        val lowest = assembled.parts.minBy { assertNotNull(it.box).low.y }
        assertEquals("foot-bird", lowest.template.id.value)
        val feet = assembled.parts.filter { it.template.id.value == "foot-bird" }
        assertEquals(2, feet.size)
        feet.forEach { assertEquals(assembled.floor, assertNotNull(it.box).low.y, ON_THE_GROUND) }
        assembled.parts.filter { it.template.id.value == "tail-feathers" }.forEach { assertTrue(assertNotNull(it.box).low.y > assembled.floor, "the tail clears the ground") }
    }

    @Test
    fun theBirdFootHasThreeToesForwardAndOneBack() {
        val foot = template("foot-bird")
        val axes = PartBuilder.axes(foot)
        val toes = PartBuilder.build(foot).filter { it.name.startsWith("toes:") }.associateBy { it.name }
        assertEquals(4, toes.size)
        fun run(name: String): Pair<Vec3, Vec3> = assertNotNull(toes.getValue(name).slices).let { it.first().frame.center to it.last().frame.center }
        val forward = (0..2).map { run("toes:$it") }
        forward.forEach { (root, tip) -> assertTrue((tip - root) dot axes.along > 0f, "a front toe runs forward") }
        val across = forward.map { (_, tip) -> tip dot axes.side }
        assertEquals(across.sorted(), across, "the front toes fan across the foot in order")
        assertTrue(across.first() < 0f && across.last() > 0f, "the outer front toes spread either side")
        val (root, tip) = run("toes:3")
        assertTrue((tip - root) dot axes.along < 0f, "the hind toe runs back")
        assertTrue(tip dot axes.along < 0f, "the hind toe reaches behind the ankle")
    }

    @Test
    fun theBirdLegStandsDigitigradeAndBearsWeight() {
        val leg = template("leg-bird")
        assertTrue(leg.axis.bearsWeight)
        assertEquals(listOf("hip", "heel", "ankle"), leg.blocks.flatMap { it.joints() }.map { it.name })
        assertEquals("foot-bird", assertNotNull(shipped.firstTaken(socketsOn(plan, leg, emptyMap()).single())).id.value)
        val axes = PartBuilder.axes(leg)
        val body = assertNotNull(PartBuilder.build(leg).single().slices)
        val heel = body.minBy { kotlin.math.abs(it.u - 0.5f) }.frame.center
        val line = (body.first().frame.center + body.last().frame.center) * 0.5f
        assertTrue((heel - line) dot axes.up < 0f, "the heel stands back of the line from hip to ankle")
    }

    private fun ids(): () -> String {
        var next = 0
        return { "id-${next++}" }
    }
}
