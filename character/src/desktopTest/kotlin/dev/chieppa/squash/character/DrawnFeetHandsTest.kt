package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Field
import dev.chieppa.squash.character.build.LimbEnds
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A foot or hand drawn from a sheet takes its drawing as its solid, smooth to the drawn toe end, and its ankle or wrist is as thick as the
 * limb it sits on, so the two meet with no step.
 */
class DrawnFeetHandsTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val foot = shipped.parts.single { it.id.value == "foot" && it.version == 10 }

    /** A closed outline through [points], each side a straight cubic. */
    private fun outline(vararg points: Pair<Float, Float>): OutlineCurve {
        val loop = points.toList() + points.first()
        val curve = mutableListOf(CurvePoint(loop[0].first, loop[0].second))
        loop.zipWithNext { a, b ->
            for (k in 1..3) curve += CurvePoint(a.first + (b.first - a.first) * k / 3f, a.second + (b.second - a.second) * k / 3f)
        }
        return OutlineCurve(curve)
    }

    /** A socked toon foot from the side: a round heel behind the ankle, a flat sole, the instep falling to a round toe. */
    private val side = outline(-0.05f to -0.01f, -0.03f to 0.02f, 0.03f to 0.01f, 0.12f to -0.01f, 0.16f to -0.025f, 0.13f to -0.035f, -0.03f to -0.035f)

    /** The same foot from above, wider at the toes than at the heel. */
    private val top = outline(-0.05f to 0f, -0.02f to 0.04f, 0.1f to 0.065f, 0.16f to 0f, 0.1f to -0.065f, -0.02f to -0.04f)

    @Test
    fun aDrawnFootTakesItsDrawingAsItsSolid() {
        val patches = PartBuilder.build(foot, mapOf("toes" to 0f), mapOf(View.SIDE to side, View.TOP to top))
        val body = patches.first { it.name == "body" }
        val slices = assertNotNull(body.slices)
        val axes = PartBuilder.axes(foot)
        val along = slices.map { it.frame.center dot axes.along }
        assertEquals(-0.05f, along.min(), 0.004f, "it reaches back to the drawn heel")
        assertEquals(0.16f, along.max(), 0.004f, "and forward to the drawn toe, toes and all")
        val ball = slices.minBy { kotlin.math.abs((it.frame.center dot axes.along) - 0.1f) }
        assertEquals(0.13f, ball.width, 0.01f, "as wide as drawn across the ball")
        assertTrue(slices.drop(2).dropLast(2).zipWithNext().all { (a, b) -> kotlin.math.abs(a.width - b.width) < 0.02f }, "its width changes smoothly ring to ring, with no ragged end")
    }

    @Test
    fun aDrawnFootKeepsOnlyItsAnkleOfTheTemplatesSolids() {
        val field = foot.blocks.filterIsInstance<Field>().single()
        assertTrue(field.fit && field.drawn)
        assertEquals(listOf("ankle"), field.parts.filter { it.kept }.map { it.name })
    }

    private val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1))
    private val leg = PartInstance(PartId("leg"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"))
    private val paw = PartInstance(PartId("paw"), TemplateRef(TemplateId("paw"), 12), SocketRef(leg.id, "paw"))
    private val character: Character by lazy {
        AddParts(listOf(torso, leg, paw)).apply(SquashDocument(Character(CharacterId("c"), "Fennec", plan))).document.character
    }

    @Test
    fun aPawsAnkleIsTheLegsEnd() {
        val radius = assertNotNull(LimbEnds.endRadius(character, leg.id, library))
        val data = LimbEnds.withLimb(character, paw.id, library, PartData())
        val template = library[paw.template]
        val limb = assertNotNull(data.params[LimbEnds.LIMB_PARAM])
        assertEquals(radius, limb * template.axis.length, 1e-5f, "the ankle's radius is the leg end's, as a share of the paw's length")
        val set = LimbEnds.withLimb(character, paw.id, library, PartData(params = mapOf(LimbEnds.LIMB_PARAM to 0.2f)))
        assertEquals(0.2f, set.params[LimbEnds.LIMB_PARAM], "a value the part sets stays")
    }

    @Test
    fun theLegRunsIntoThePawWithNoStep() {
        val built = assembleNow(assemblyParts(character), { id -> LimbEnds.withLimb(character, id, library, character.resolved(id) ?: PartData()) }, library.plan(plan), library)
        val field = assertNotNull(fieldOf(built, RunSolids())).field
        val socket = assertNotNull(built.part(paw.id)).origin
        val radius = assertNotNull(LimbEnds.endRadius(character, leg.id, library))
        fun across(height: Float): Float {
            val middle = socket + Vec3(0f, height, 0f)
            fun reach(direction: Vec3): Float {
                var inside = 0f
                var outside = radius * 4f
                repeat(30) {
                    val mid = (inside + outside) / 2f
                    if (field.value(middle + direction * mid) < 0f) inside = mid else outside = mid
                }
                return inside
            }
            return reach(Vec3(0f, 0f, 1f)) + reach(Vec3(0f, 0f, -1f))
        }
        val leg = across(radius * 3f)
        for (height in listOf(radius * 0.5f, radius, radius * 2f)) {
            val there = across(height)
            assertTrue(there >= leg * 0.9f, "no waist at ${height / radius} radii above the ankle: $there across, the leg $leg")
            assertTrue(there <= leg * 1.25f, "no collar at ${height / radius} radii above the ankle: $there across, the leg $leg")
        }
    }
}
