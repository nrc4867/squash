package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.outlineEnds
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.StrokeEnds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAMPLES = 96

/** A paw has no socket plane, so its drawing reaches behind its socket and takes in the end of the leg it sits on. */
class PawAroundLegTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private fun ref(id: String) = TemplateRef(TemplateId(id), 1)
    private val torso = PartInstance(PartId("torso"), ref("torso"))
    private val leg = PartInstance(PartId("leg"), ref("leg"), SocketRef(torso.id, "front-leg"))
    private val paw = PartInstance(PartId("paw"), ref("paw"), SocketRef(leg.id, "paw"))
    private val around = outline(-0.12f to 0f, -0.08f to 0.1f, 0.1f to 0.12f, 0.26f to 0.06f, 0.3f to 0f, 0.26f to -0.09f, 0.1f to -0.1f, -0.08f to -0.09f, -0.12f to 0f)

    @Test
    fun aPawStrokeIsNeverCutAtASocketPlane() {
        val template = template("paw")
        for (view in View.entries) assertEquals(StrokeEnds.Closed, outlineEnds(view, template.openEnd), "$view")
    }

    @Test
    fun aDrawnPawCoversTheEndOfTheLegInSide() {
        val empty = SquashDocument(Character(CharacterId("c"), "Fennec", plan))
        val document = SetOutline(paw.id, View.SIDE, around).apply(AddParts(listOf(torso, leg, paw)).apply(empty).document).document
        val character = document.character
        val built = assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(plan), library)
        val legEnd = (0 until SAMPLES).map { assertNotNull(assertNotNull(built.part(leg.id)).pointOn(1f, it / SAMPLES.toFloat())) }
        val placed = assertNotNull(built.part(paw.id))
        val pawPoints = (0..SAMPLES).flatMap { i -> (0 until SAMPLES).mapNotNull { j -> placed.pointOn(i / SAMPLES.toFloat(), j / SAMPLES.toFloat()) } }
        assertTrue(pawPoints.minOf { it.x } < legEnd.minOf { it.x }, "it reaches behind the leg")
        assertTrue(pawPoints.maxOf { it.x } > legEnd.maxOf { it.x }, "and past its front")
        assertTrue(pawPoints.maxOf { it.y } > legEnd.maxOf { it.y }, "and above its end")
        assertTrue(pawPoints.minOf { it.y } < legEnd.minOf { it.y }, "and below it")
    }

    @Test
    fun aPawDrawnAroundItsSocketHoldsAtEveryToeSetting() {
        val template = template("paw")
        for (toes in listOf(0f, 3f, 4f, 5f)) {
            val patches = PartBuilder.build(template, mapOf("toes" to toes), mapOf(View.SIDE to around))
            for (patch in PartBuilder.mesh("paw", template, patches).patches) {
                assertEquals(emptyList(), MeshInvariants.check(patch, PartBuilder.openRings(template, patch.patch)), "$toes toes")
            }
        }
    }
}
