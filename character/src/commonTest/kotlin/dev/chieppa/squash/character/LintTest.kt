package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.block.Cap
import dev.chieppa.squash.character.block.Joint
import dev.chieppa.squash.character.block.Landmark
import dev.chieppa.squash.character.block.Pad
import dev.chieppa.squash.character.block.Socket
import dev.chieppa.squash.character.block.ToeRow
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
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.model.Topology
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
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
        views: Map<View, Sets> = mapOf(View.SIDE to Sets.HEIGHT, View.TOP to Sets.WIDTH, View.FRONT to Sets.CAP),
        id: String = "leg",
    ) = PartTemplate(
        format = 1,
        id = TemplateId(id),
        version = 1,
        name = "Leg",
        axis = AxisSpec(Direction(0f, -1f, 0f)),
        openEnd = openEnd,
        topology = Topology(rings, 8),
        views = views,
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
        val nowhere = Pad(size = Param.of(0.1f), rings = 7, segments = 12)
        val onNothing = Pad(on = "toes", size = Param.of(0.1f), rings = 7, segments = 12)
        assertEquals(listOf("$.blocks[1].u", "$.blocks[2].on"), paths(template(listOf(tube, nowhere, onNothing))))
    }

    @Test
    fun aToePadComesAfterItsRow() {
        val row = ToeRow("toes", u = Span(0.5f, 1f), count = Param.of(4f), spread = Param.of(10f), radius = Param.of(0.1f), rings = 10, segments = 8)
        val pad = Pad(on = "toes", size = Param.of(0.1f), rings = 7, segments = 12)
        assertEquals(emptyList(), paths(template(listOf(tube, row, pad))))
        assertEquals(listOf("$.blocks[1].on"), paths(template(listOf(tube, pad, row))))
    }

    @Test
    fun theAxisBendMustNameAParameterAndSitOnTheAxis() {
        val bent = template(listOf(tube))
        val missing = bent.copy(axis = bent.axis.copy(bend = Param("\$nope")))
        assertEquals(listOf("$.axis.bend"), paths(missing))
        val outside = bent.copy(axis = bent.axis.copy(bendAt = 1.5f))
        assertEquals(listOf("$.axis.bendAt"), paths(outside))
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
    fun exactlyOneRunReachesTheOpenEnd() {
        val other = Tube(name = "second", u = Span(0f, 0.5f), radius = Param.of(0.2f))
        val late = Tube(u = Span(0.2f, 1f), radius = Param.of(0.2f))
        assertEquals(emptyList(), paths(template(listOf(tube))))
        assertEquals(emptyList(), paths(template(listOf(tube, other), openEnd = End.END)))
        assertEquals(listOf("$.openEnd"), paths(template(listOf(tube, other))))
        assertEquals(listOf("$.openEnd"), paths(template(listOf(late))))
        assertEquals(emptyList(), paths(template(listOf(late), openEnd = null)))
    }

    @Test
    fun aSocketSitsWhereARunReaches() {
        val short = Tube(u = Span(0f, 0.6f), radius = Param.of(0.2f))
        val onIt = Socket("paw", u = 0.5f, v = 0.5f)
        val pastIt = Socket("paw", u = 0.9f, v = 0.5f)
        assertEquals(emptyList(), paths(template(listOf(short, onIt))))
        assertEquals(listOf("$.blocks[1].u"), paths(template(listOf(short, pastIt))))
    }

    @Test
    fun aSocketNeedsARunThatIsOnWheneverItIs() {
        val params = mapOf("extra" to ParamSpec(ParamDefault(1f, isFlag = true)))
        val flag = ParamRef("\$extra")
        val switched = Tube(name = "extra", condition = flag, u = Span(0.5f, 1f), radius = Param.of(0.2f))
        val base = Tube(u = Span(0f, 0.5f), radius = Param.of(0.2f))
        val onSwitched = Socket("tip", condition = flag, u = 0.9f, v = 0.5f)
        val always = Socket("tip", u = 0.9f, v = 0.5f)
        assertEquals(emptyList(), paths(template(listOf(base, switched, onSwitched), params = params)))
        assertEquals(listOf("$.blocks[2].u"), paths(template(listOf(base, switched, always), params = params)))
    }

    @Test
    fun aPlanSocketSitsWhereARunOfItsTemplateReaches() {
        val torsoId = TemplateId("torso")
        val short = template(listOf(Tube(u = Span(0f, 0.6f), radius = Param.of(0.2f))), id = "torso")
        fun plan(u: Float) = BodyPlan(
            format = 1,
            id = BodyPlanId("plan"),
            version = 1,
            name = "Plan",
            root = PlanRoot(torsoId, Direction(0f, -1f, 0f)),
            sockets = listOf(PlanSocket("leg", torsoId, u, 0.35f, takes = emptyList())),
        )
        assertEquals(emptyList(), BodyPlanLint.lint(plan(0.5f), listOf(short)))
        assertEquals(listOf("$.sockets[0].u"), BodyPlanLint.lint(plan(0.9f), listOf(short)).map { it.path })
    }

    @Test
    fun noTwoBlocksBuildAPatchOfTheSameName() {
        fun run(name: String?, u: Span = Span(0.5f, 1f)) = Tube(name = name, u = u, radius = Param.of(0.2f))
        fun row(name: String) = ToeRow(name, u = Span(0.5f, 1f), count = Param.of(4f), spread = Param.of(10f), radius = Param.of(0.1f), rings = 10, segments = 8)
        fun pad(name: String?, on: String? = null) = Pad(name = name, u = if (on == null) 0.5f else null, on = on, size = Param.of(0.1f), rings = 7, segments = 12)
        fun collisions(vararg blocks: Block) = TemplateLint.lint(template(listOf(tube) + blocks)).filter { it.message.contains("patches named") }.map { it.message }

        assertEquals(emptyList(), collisions(run("muzzle"), row("toes"), pad("palm"), pad(null, on = "toes")))
        assertEquals(listOf("two patches named 'body'"), collisions(run(null)))
        assertEquals(listOf("two patches named 'muzzle'"), collisions(run("muzzle"), run("muzzle")))
        assertEquals(listOf("two patches named 'toes'"), collisions(row("toes"), row("toes")))
        assertEquals(listOf("two patches named 'toes:1'"), collisions(row("toes"), run("toes:1")))
        assertEquals(listOf("two patches named 'pad:palm'"), collisions(pad("palm"), pad("palm")))
        assertEquals(listOf("two patches named 'pad:toes'"), collisions(row("toes"), pad(null, on = "toes"), pad(null, on = "toes")))
    }

    @Test
    fun theViewsMapMakesSense() {
        fun views(vararg entries: Pair<View, Sets>) = paths(template(listOf(tube), views = mapOf(*entries)))
        assertEquals(emptyList(), views(View.SIDE to Sets.HEIGHT))
        assertEquals(emptyList(), views(View.SIDE to Sets.HEIGHT, View.TOP to Sets.WIDTH, View.FRONT to Sets.SECTION))
        assertEquals(emptyList(), views(View.TOP to Sets.HEIGHT, View.SIDE to Sets.WIDTH))
        assertEquals(listOf("$.views"), views())
        assertEquals(listOf("$.views"), views(View.FRONT to Sets.CAP))
        assertEquals(listOf("$.views"), views(View.SIDE to Sets.HEIGHT, View.TOP to Sets.HEIGHT))
        assertEquals(listOf("$.views.side"), views(View.SIDE to Sets.CAP, View.TOP to Sets.WIDTH))
        assertEquals(listOf("$.views.front"), views(View.SIDE to Sets.HEIGHT, View.FRONT to Sets.WIDTH))
    }

    @Test
    fun capAndSectionAreNotBothSet() {
        val findings = TemplateLint.lint(
            template(listOf(tube), views = mapOf(View.SIDE to Sets.HEIGHT, View.TOP to Sets.CAP, View.FRONT to Sets.SECTION)),
        )
        assertTrue(findings.any { it.path == "$.views" && it.message.contains("cap and section") })
    }

    @Test
    fun aToeOrPadGridIsBigEnoughToBuild() {
        val row = ToeRow("toes", u = Span(0.5f, 1f), count = Param.of(4f), spread = Param.of(10f), radius = Param.of(0.1f), rings = 2, segments = 2)
        val pad = Pad(u = 0.5f, size = Param.of(0.1f), rings = 2, segments = 2)
        assertEquals(
            listOf("$.blocks[1].rings", "$.blocks[1].segments", "$.blocks[2].rings", "$.blocks[2].segments"),
            paths(template(listOf(tube, row, pad))),
        )
    }

    @Test
    fun aPlansRootLiesAsItsTemplateDoes() {
        val torsoId = TemplateId("torso")
        val torso = template(listOf(Tube(u = Span(0f, 1f), radius = Param.of(0.2f))), id = "torso")
        val plan = BodyPlan(format = 1, id = BodyPlanId("plan"), version = 1, name = "Plan", root = PlanRoot(torsoId, Direction(1f, 0f, 0f)), sockets = emptyList())
        assertEquals(listOf("$.root.rest"), BodyPlanLint.lint(plan, listOf(torso)).map { it.path })
    }

    @Test
    fun aPlanMustNameShippedTemplatesUnlessOptional() {
        val torso = TemplateId("torso")
        val plan = BodyPlan(
            format = 1,
            id = BodyPlanId("plan"),
            version = 1,
            name = "Plan",
            root = PlanRoot(torso, Direction(0f, -1f, 0f)),
            sockets = listOf(
                PlanSocket("ruff", torso, 0.9f, 0.5f, takes = listOf(TemplateId("ruff")), optional = true),
                PlanSocket("leg", torso, 0.9f, 0.35f, takes = listOf(TemplateId("leg"))),
                PlanSocket("twin", torso, 0.9f, 0.5f, pair = Mirror.X, takes = emptyList()),
            ),
        )
        val findings = BodyPlanLint.lint(plan, listOf(template(listOf(tube), id = "torso")))
        assertEquals(listOf("$.sockets[1].takes", "$.sockets[2].v"), findings.map { it.path })
    }
}
