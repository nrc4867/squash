package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.asBuilt
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.MeshInvariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val BODY = PartId("body")
private val LIMB = PartId("limb")
private const val SIDE_SOCKET = 1
private const val REAR_SOCKET = 2

/** Joints, landmarks and a rest direction placed by hand: parameters a part's build reads. See HLD: data-model, Joints and landmarks by hand. */
class HandAnatomyTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val tube = assertNotNull(shipped.template(TemplateId("tube")))
    private val blank = assertNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("blank") })

    private fun document(vararg commands: DocumentCommand): SquashDocument = commands.fold(
        SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1))),
    ) { held, command -> command.apply(held).document }

    private fun body(vararg more: DocumentCommand) = document(
        AddParts(listOf(PartInstance(BODY, tube.ref, role = Role.BODY))),
        HandSockets.add(BODY, SIDE_SOCKET, 0.3f, 0.3f, 0f, paired = false),
        HandSockets.add(BODY, REAR_SOCKET, 0.0f, 0.1f, 0f, paired = false),
        *more,
    )

    @Test
    fun aJointPlacedByHandIsOneStepThatTheBuildReadsAndTheTipFollows() {
        val params = HandJoints.add(LIMB, 1, 0.5f, Hinge.Z, Limits(-20f, 70f), stretch = true).values.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        val built = tube.asBuilt(params, onSocket = true)
        val joints = built.blocks.flatMap { it.joints() }
        assertEquals(listOf("base", HandJoints.name(1), HandJoints.TIP), joints.map { it.name })
        val made = joints[1]
        assertEquals(Hinge.Z, made.hinge)
        assertEquals(Limits(-20f, 70f), made.limits)
        assertTrue(made.stretch)
        assertEquals(emptyList(), TemplateLint.lint(built), "the built template still lints clean")
        assertEquals(tube, tube.asBuilt(emptyMap(), onSocket = true), "a part with none builds as its template says")
    }

    @Test
    fun theMeshTagsVerticesByTheJointsPlacedByHand() {
        val params = mapOf(HandJoints.u(1) to 0.5f)
        val built = tube.asBuilt(params, onSocket = true)
        val patch = PartBuilder.mesh("limb", built, PartBuilder.build(built, params)).patches.single()
        assertEquals(setOf(0, 1), patch.bones.toSet(), "the base owns up to the joint, the joint the rest")
        assertEquals(emptyList(), MeshInvariants.check(patch, setOf(0)))
    }

    @Test
    fun deletingAJointOrALandmarkTakesItsParametersAway() {
        val placed = body(
            AddParts(listOf(PartInstance(LIMB, tube.ref, SocketRef(BODY, HandSockets.name(SIDE_SOCKET))))),
            HandJoints.add(LIMB, 1, 0.5f, Hinge.X, HandJoints.DEFAULT_LIMITS, stretch = false),
            HandLandmarks.add(LIMB, 1, 0.8f, 0.2f),
        )
        val limb = assertNotNull(placed.character.part(LIMB))
        val built = tube.asBuilt(placed.character.resolvedShape(limb).params, onSocket = true)
        assertEquals(listOf(HandLandmarks.name(1)), built.blocks.flatMap { it.landmarks() }.map { it.name })
        val cleared = listOf(HandJoints.delete(LIMB, 1), HandLandmarks.delete(LIMB, 1)).fold(placed) { held, command -> command.apply(held).document }
        val params = cleared.character.resolvedShape(assertNotNull(cleared.character.part(LIMB))).params
        assertEquals(emptyList(), HandJoints.ids(params) + HandLandmarks.ids(params))
    }

    @Test
    fun aTubeOnTheRearEndRestingBackIsGuessedATail() {
        val onRear = PartInstance(LIMB, tube.ref, SocketRef(BODY, HandSockets.name(REAR_SOCKET)))
        val forward = body(AddParts(listOf(onRear)))
        assertEquals(Role.OTHER, shipped.roles(forward.character).guess(onRear), "a tube resting forward points into the body")
        val back = RestDirection.restIn(LIMB, RestDirection.BACK).apply(forward).document
        val limb = assertNotNull(back.character.part(LIMB))
        assertEquals(Role.TAIL, shipped.roles(back.character).guess(limb))
        val placed = assemble(back).part(LIMB)
        assertTrue(assertNotNull(placed).template.axis.rest.x < -0.99f, "it rests back along the body")
    }

    @Test
    fun aTubeOnTheSideRestingDownIsGuessedALeg() {
        val onSide = PartInstance(LIMB, tube.ref, SocketRef(BODY, HandSockets.name(SIDE_SOCKET)))
        val down = body(AddParts(listOf(onSide)), RestDirection.restIn(LIMB, RestDirection.DOWN))
        assertEquals(Role.LEG, shipped.roles(down.character).guess(assertNotNull(down.character.part(LIMB))))
    }

    private fun assemble(document: SquashDocument) =
        assembleNow(assemblyParts(document.character), { id -> document.character.resolved(assertNotNull(document.character.part(id))) }, blank, library)
}
