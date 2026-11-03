package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.block.Cap
import dev.chieppa.squash.character.block.Joint
import dev.chieppa.squash.character.block.Landmark
import dev.chieppa.squash.character.block.Pad
import dev.chieppa.squash.character.block.Socket
import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.lint.BodyPlanLint
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.AxisSpec
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Direction
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamDefault
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.ParamSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.PlanRoot
import dev.chieppa.squash.character.model.PlanSocket
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.model.Topology
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LintTest {
    private val tube = Tube(u = Span(0f, 1f), radius = Param.of(0.2f))

    private fun template(
        blocks: List<Block>,
        rings: Int = 24,
        openEnd: End? = End.START,
        params: Map<String, ParamSpec> = emptyMap(),
        section: SectionSpec = SectionSpec.Named(dev.chieppa.squash.geom.SectionProfile.CIRCLE),
    ) = PartTemplate(
        format = 1,
        id = TemplateId("leg"),
        version = 1,
        name = "Leg",
        axis = AxisSpec(Direction(0f, -1f, 0f)),
        openEnd = openEnd,
        topology = Topology(rings, 8),
        views = emptyMap(),
        section = section,
        params = params,
        blocks = blocks,
    )

    private fun paths(template: PartTemplate) = TemplateLint.lint(template).map { it.path }

    private fun joint(name: String, u: Float) = Joint(name, u = u, hinge = Hinge.X)

    @Test
    fun aGoodTemplateHasNoFindings() {
        assertEquals(emptyList(), TemplateLint.lint(template(listOf(tube, joint("a", 0f), joint("b", 1f)))))
    }

    @Test
    fun uMustBeInsideZeroToOne() {
        assertEquals(listOf("$.blocks[0].u"), paths(template(listOf(Tube(u = Span(0f, 1.5f), radius = Param.of(0.2f))))))
        assertEquals(listOf("$.blocks[1].u"), paths(template(listOf(tube, joint("a", -0.1f)))))
    }

    @Test
    fun jointsMustBeOrderedAlongTheAxis() {
        val findings = TemplateLint.lint(template(listOf(tube, joint("knee", 0.6f), joint("hip", 0.1f))))
        assertTrue(findings.single().message.contains("hip"))
    }

    @Test
    fun ringsMustBeEnoughForTheJoints() {
        val blocks = listOf(tube, joint("a", 0f), joint("b", 0.5f), joint("c", 1f))
        assertEquals(listOf("$.topology.rings"), paths(template(blocks, rings = 5)))
    }

    @Test
    fun theOpenEndCannotBeCapped() {
        assertEquals(listOf("$.blocks[1].end"), paths(template(listOf(tube, Cap(end = End.START)))))
    }

    @Test
    fun paramReferencesMustExistWithTheRightType() {
        val numberParams = mapOf("size" to ParamSpec(ParamDefault(1f)), "pads" to ParamSpec(ParamDefault(1f, isFlag = true)))
        val missing = Tube(u = Span(0f, 1f), radius = Param("\$nope"))
        assertEquals(listOf("$.blocks[0].radius"), paths(template(listOf(missing))))
        val flagAsNumber = Tube(u = Span(0f, 1f), radius = Param("\$pads"))
        assertEquals(listOf("$.blocks[0].radius"), paths(template(listOf(flagAsNumber), params = numberParams)))
        val numberAsFlag = Tube(condition = ParamRef("\$size"), u = Span(0f, 1f), radius = Param.of(0.2f))
        assertEquals(listOf("$.blocks[0].if"), paths(template(listOf(numberAsFlag), params = numberParams)))
    }

    @Test
    fun aDefaultMustBeInsideItsRange() {
        val params = mapOf("size" to ParamSpec(ParamDefault(3f), min = 0f, max = 2f))
        assertEquals(listOf("$.params.size"), paths(template(listOf(tube), params = params)))
    }

    @Test
    fun aPairedPointMustSitOffItsMirror() {
        val socket = Socket("ear", u = 0.2f, v = 0.5f, pair = Mirror.X)
        val landmark = Landmark("eye", u = 0.2f, v = 0f, pair = Mirror.X)
        assertEquals(listOf("$.blocks[1].v", "$.blocks[2].v"), paths(template(listOf(tube, socket, landmark))))
    }

    @Test
    fun aPadSitsOnAToeRowOrAtAU() {
        val nowhere = Pad(size = Param.of(0.1f))
        val onNothing = Pad(on = "toes", size = Param.of(0.1f))
        assertEquals(listOf("$.blocks[1].u", "$.blocks[2].on"), paths(template(listOf(tube, nowhere, onNothing))))
    }

    @Test
    fun controlPointsMustMatchTheSegments() {
        val section = SectionSpec.Points(List(5) { Vec2(it.toFloat(), 0f) })
        assertEquals(listOf("$.section"), paths(template(listOf(tube), section = section)))
    }

    @Test
    fun namesAreUniqueWithinAKind() {
        assertTrue(TemplateLint.lint(template(listOf(tube, joint("a", 0f), joint("a", 1f)))).single().message.contains("'a'"))
    }

    @Test
    fun aPlanMustNameShippedTemplatesUnlessOptional() {
        val torso = TemplateId("torso")
        val plan = BodyPlan(
            format = 1,
            id = BodyPlanId("plan"),
            version = 1,
            name = "Plan",
            root = PlanRoot(torso, Direction(1f, 0f, 0f)),
            sockets = listOf(
                PlanSocket("ruff", torso, 0.9f, 0.5f, takes = listOf(TemplateId("ruff")), optional = true),
                PlanSocket("leg", torso, 0.9f, 0.35f, takes = listOf(TemplateId("leg"))),
                PlanSocket("twin", torso, 0.9f, 0.5f, pair = Mirror.X, takes = emptyList()),
            ),
        )
        val findings = BodyPlanLint.lint(plan, setOf(torso))
        assertEquals(listOf("$.sockets[1].takes", "$.sockets[2].v"), findings.map { it.path })
    }
}
