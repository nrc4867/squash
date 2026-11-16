package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.firstRunIndex
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.geom.field.JoinedMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val WOMAN_HEAD_ID = PartId("head")
private val WOMAN_CAP_ID = PartId("hair")
private val WOMAN_TORSO_ID = PartId("torso")

/** How far along a hair part, in its u, its socket's bone may still hold it: its root band, held to half the way to its second joint. */
private const val ROOT_BAND = 0.175f

/** How far the head turns to look over a shoulder, in degrees. */
private const val LOOK = 70f

/** How far inside the torso a pushed hair vertex may lie, in body units: the meshes' own error against the field. */
private const val INSIDE = 0.004f

/**
 * The example woman's hair: a cap in her field and a fall and locks of their own meshes. The cap follows the head alone; each part of its
 * own follows its own chain and the head only round its root; and with the head turned hard over a shoulder, no hair lies inside her torso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HairRigTest {
    private val document = readDocument(Path.of(System.getProperty("squash.examples"), "woman.json").readText())

    @Test
    fun hairFollowsItsOwnBonesAndTheHeadAloneAndClearsTheBodyInAHeadTurn() = runTest {
        val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        val shown = pose.surface
        runCurrent()
        check(session.graph.settled.value)
        val rig = assertNotNull(pose.rig.rig.output.value)
        val hair = document.character.parts.filter { it.role == Role.HAIR && it.id != WOMAN_CAP_ID }.map { it.id }
        assertTrue(hair.size >= 3, "the fall and the locks are hair of their own")
        for (id in hair) {
            val part = assertNotNull(rig.part(id), "$id is rigged")
            for (v in 0 until part.weights.vertexCount) for ((bone, weight) in part.weights.of(v)) {
                val from = rig.bones[bone].name.part
                assertTrue(from == id || from == WOMAN_HEAD_ID, "$id vertex $v follows ${rig.bones[bone].name} by $weight")
                if (from == WOMAN_HEAD_ID) assertTrue(part.vertices[v].u <= ROOT_BAND, "$id vertex $v at u ${part.vertices[v].u} follows the head past its root")
            }
        }
        val held = assertNotNull(shown.skin.output.value)
        val surface = assertNotNull(held.surface)
        val skin = assertNotNull(held.skin)
        val runs = surface.field.runs
        var capped = 0
        for (v in 0 until surface.mesh.vertexCount) {
            val own = surface.mesh.winners[v]
            if (own < 0 || runs[own].partId != WOMAN_CAP_ID) continue
            capped++
            for (slot in 0 until MAX_INFLUENCES) {
                val bone = skin.bones[v * MAX_INFLUENCES + slot]
                if (bone >= 0) assertTrue(rig.bones[bone].name.part == WOMAN_HEAD_ID, "cap vertex $v follows ${rig.bones[bone].name}")
            }
        }
        assertTrue(capped > 0, "the cap is in her field")

        val torso = assertNotNull(session.assembly.output.value?.part(WOMAN_TORSO_ID))
        val patch = torso.surface.patches[firstRunIndex(torso.surface.patches)]
        val solid = JoinedMember(RunSolids().of(patch), torso.origin, torso.mirrored, 0f)
        for (turn in listOf(LOOK, -LOOK)) {
            session.send(SetPose(Pose(listOf(BonePose(WOMAN_HEAD_ID, "neck", turn = turn, mode = JointMode.FREE)))))
            runCurrent()
            val body = assertNotNull(session.posedBody.output.value)
            for (id in hair) {
                val placed = assertNotNull(body.part(id))
                for (v in 0 until placed.surface.mesh.vertexCount) {
                    val p = placed.origin + placed.surface.mesh.position(v)
                    assertTrue(solid.own(p) > -INSIDE, "$id vertex $v lies ${-solid.own(p)} inside the torso with the head turned $turn")
                }
            }
        }
    }
}
