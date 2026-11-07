package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.savedTemplate
import dev.chieppa.squash.character.build.savedTemplateId
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.freeId
import dev.chieppa.squash.character.load.save
import dev.chieppa.squash.character.load.withSaved
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.MeshInvariants
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Save as template: a hand-built part written as a template file that loads, lints clean and builds. See HLD: data-model, Save as template. */
class SavedTemplateTest {
    private val tube = assertNotNull(shipped.template(TemplateId("tube")))

    /** A hand-built leg: a thinner tube of fewer rings resting down, with a knee, a socket at its end and a landmark. */
    private val leg: Map<String, Float> = buildMap {
        put("girth", 0.07f)
        put("size", 0.6f)
        put(GenericParams.RINGS, 24f)
        put(GenericParams.SECTION, 1f)
        put(PlacementParams.REST, RestDirection.DOWN.ordinal.toFloat())
        put(PlacementParams.OUTWARD, 20f)
        HandJoints.add(PartId("x"), 1, 0.5f, Hinge.X, Limits(0f, 120f), stretch = false).values.forEach { (k, v) -> v?.let { put(k, it) } }
        HandSockets.add(PartId("x"), 1, 1f, 0.5f, 0f, paired = false).values.forEach { (k, v) -> v?.let { put(k, it) } }
        HandLandmarks.add(PartId("x"), 1, 0.9f, 0.5f).values.forEach { (k, v) -> v?.let { put(k, it) } }
    }

    @Test
    fun aHandBuiltPartBecomesATemplateThatLintsClean() {
        val saved = savedTemplate(tube, leg, Role.LEG, TemplateId("lizard-leg"), "Lizard leg")
        assertEquals(emptyList(), TemplateLint.lint(saved))
        assertNull(saved.generic, "its shape choices are fixed")
        assertEquals(1, saved.version)
        assertEquals(Role.LEG, saved.role)
        assertEquals(24, saved.topology.rings)
        assertEquals(0.07f, saved.params.getValue("girth").default.value)
        assertEquals(listOf("base", "joint1", HandJoints.TIP), saved.blocks.flatMap { it.joints() }.map { it.name })
        assertEquals(listOf("socket1"), saved.blocks.flatMap { it.sockets() }.map { it.name })
        assertEquals(listOf("landmark1"), saved.blocks.flatMap { it.landmarks() }.map { it.name })
        assertTrue(saved.axis.rest.y < -0.8f, "it rests down, leaning out as the part did")
    }

    @Test
    fun itIsWrittenLoadedAfterTheShippedOnesAndAPartMadeFromItBuilds() {
        val folder = Files.createTempDirectory("saved-templates")
        val catalog = shipped.withSaved(folder)
        val id = catalog.freeId(assertNotNull(savedTemplateId("Lizard leg")))
        assertEquals(TemplateId("lizard-leg"), id)
        val saved = savedTemplate(tube, leg, Role.LEG, id, "Lizard leg")
        assertTrue(assertNotNull(catalog.save(saved)).exists())
        val loaded = shipped.withSaved(folder)
        assertEquals(saved, loaded.parts.last(), "it loads after the shipped templates as it was written")
        assertEquals(TemplateId("lizard-leg-2"), loaded.freeId(id), "a second of that name takes the next free id")

        val body = PartId("body")
        val limb = PartId("limb")
        val document = listOf(
            AddParts(listOf(PartInstance(body, tube.ref, role = Role.BODY))),
            HandSockets.add(body, 1, 0.3f, 0.3f, 0f, paired = false),
            AddParts(listOf(PartInstance(limb, saved.ref, SocketRef(body, HandSockets.name(1)), role = Role.LEG))),
        ).fold(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))) { held, command -> command.apply(held).document }
        val library = TemplateLibrary(loaded.parts, loaded.plans)
        val plan = loaded.plans.first { it.id == BodyPlanId("blank") }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        val placed = assertNotNull(assembled.part(limb))
        assertEquals(listOf("base", "joint1", HandJoints.TIP), placed.template.blocks.flatMap { it.joints() }.map { it.name })
        assertTrue(assembled.sockets.any { it.ref == SocketRef(limb, "socket1") }, "its socket is open for a foot")
        val mesh = PartBuilder.mesh("limb", saved, PartBuilder.build(saved))
        mesh.patches.forEach { assertEquals(emptyList(), MeshInvariants.check(it, PartBuilder.openRings(saved, it.patch))) }
    }
}
