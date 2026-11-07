package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SquashDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.math.abs
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val TORSO = PartId("torso")
private val TAIL = PartId("tail")
private val HEAD = PartId("head")

/** How far [bone]'s pivot moves under [pose], off the line its part rests along, across it and sideways out of the body's middle plane. */
private class Moved(val off: Float, val sideways: Float)

private fun Rig.moved(pose: Pose, bone: BoneName, part: PartId): Moved {
    val index = checkNotNull(indexOf(bone))
    val rest = bones[index].pivot
    val moved = boneTransforms(this, pose)[index](rest)
    val first = checkNotNull(bones.first { it.name.part == part })
    val along = (rest - first.pivot).normalized()
    val step = moved - rest
    return Moved((step - along * (step dot along)).length, abs(step.z))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpineBendTest {
    private val withers = BoneName(TORSO, "withers")

    @Test
    fun aTurnedTorsoJointBendsTheSpineInTheBodysMiddlePlane() {
        val torso = fennec.rig.bones.filter { it.name.part == TORSO }
        val length = (torso.last().pivot - torso.first().pivot).length
        val moved = fennec.rig.moved(Pose(listOf(BonePose(TORSO, "waist", 20f))), withers, TORSO)
        assertTrue(moved.off > 0.15f * length, "the withers leave the rest line by ${moved.off} of a ${length} spine")
        assertEquals(0f, moved.sideways, 1e-4f)
    }

    @Test
    fun aTurnedTailJointCurlsTheTail() {
        val tip = BoneName(TAIL, "tip")
        val moved = fennec.rig.moved(Pose(listOf(BonePose(TAIL, "tail2", 30f))), tip, TAIL)
        assertTrue(moved.off > 0.05f, "the tail's tip leaves its rest line by ${moved.off}")
        assertEquals(0f, moved.sideways, 1e-4f)
    }

    @Test
    fun theHeadTurnsAtItsBaseAndCarriesTheEars() {
        val ear = BoneName(PartId("ear-l"), "base")
        val nose = fennec.rig.moved(Pose(listOf(BonePose(HEAD, "neck", 30f))), ear, HEAD)
        assertTrue(nose.off > 1e-3f, "the ear's base moves with the head by ${nose.off}")
        assertEquals(BoneName(HEAD, "neck"), fennec.rig.bone(ear)?.parent)
    }

    @Test
    fun aCharacterPinnedToTheFirstTorsoStillRigsAndItsSpineTwists() = runTest {
        val first = savedFennec().pinnedTo(TORSO, 1)
        val session = CharacterSession(first, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        val rig = checkNotNull(node.output.value)
        assertEquals(fennec.rig.bones.size, rig.bones.size)
        val pose = Pose(listOf(BonePose(TORSO, "waist", 20f)))
        val rolled = rig.moved(pose, withers, TORSO)
        assertTrue(rolled.sideways > 1e-2f, "a turn about the spine's own length swings the withers out of the body's middle plane, ${rolled.sideways}")
    }

    private fun SquashDocument.pinnedTo(part: PartId, version: Int): SquashDocument = copy(
        character = character.copy(parts = character.parts.map { if (it.id == part) it.copy(template = it.template.copy(version = version)) else it }),
    )
}
