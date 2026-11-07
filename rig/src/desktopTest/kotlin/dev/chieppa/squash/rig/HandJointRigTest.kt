package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val BODY = PartId("body")
private val LEG = PartId("leg")
private val KNEE = HandJoints.name(1)

/** A tube given a joint by hand rigs and poses as a leg: the joint is a bone, and Turn holds it within its limits. */
@OptIn(ExperimentalCoroutinesApi::class)
class HandJointRigTest {
    private val tube = TemplateRef(TemplateId("tube"), 1)

    private val document: SquashDocument = listOf(
        AddParts(listOf(PartInstance(BODY, tube, role = Role.BODY))),
        HandSockets.add(BODY, 1, 0.3f, 0.35f, 0f, paired = false),
        AddParts(listOf(PartInstance(LEG, tube, SocketRef(BODY, HandSockets.name(1)), role = Role.LEG))),
        RestDirection.restIn(LEG, RestDirection.DOWN),
        HandJoints.add(LEG, 1, 0.5f, Hinge.X, Limits(0f, 60f), stretch = false),
    ).fold(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))) { held, command -> command.apply(held).document }

    private val rig: Rig by lazy {
        var rigged: Rig? = null
        runTest {
            val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
            val node = SessionRig(session).rig
            runCurrent()
            check(session.graph.settled.value) { "The graph did not settle" }
            rigged = checkNotNull(node.output.value)
        }
        checkNotNull(rigged)
    }

    @Test
    fun aJointPlacedByHandIsABoneBetweenTheBaseAndTheTip() {
        val names = rig.bones.filter { it.name.part == LEG }.map { it.name.joint }
        assertEquals(listOf("base", KNEE, HandJoints.TIP), names)
        assertEquals(BoneName(LEG, "base"), rig.bone(BoneName(LEG, KNEE))?.parent)
        val knee = assertNotNull(rig.bone(BoneName(LEG, KNEE)))
        assertEquals(Limits(0f, 60f), knee.limits)
        assertTrue(knee.length > 0f, "it owns the lower half")
        assertTrue(knee.pivot.y < rig.bone(BoneName(LEG, "base"))!!.pivot.y, "the leg rests down, so its knee is below its hip")
    }

    @Test
    fun turnHoldsItWithinItsLimits() {
        val tip = rig.bones.indexOfFirst { it.name == BoneName(LEG, HandJoints.TIP) }
        fun tipAt(degrees: Float) = boneTransforms(rig, Pose(listOf(BonePose(LEG, KNEE, degrees))))[tip](rig.bones[tip].pivot)
        val atLimit = tipAt(60f)
        val past = tipAt(120f)
        assertEquals(atLimit.x, past.x, 1e-4f)
        assertEquals(atLimit.y, past.y, 1e-4f)
        assertEquals(atLimit.z, past.z, 1e-4f)
        assertTrue((tipAt(30f) - atLimit).length > 1e-3f, "within its limits it turns")
        assertTrue((tipAt(-30f) - tipAt(0f)).length < 1e-4f, "it bends one way only")
    }
}
