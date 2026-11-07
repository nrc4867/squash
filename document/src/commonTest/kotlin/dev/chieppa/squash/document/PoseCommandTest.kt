package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val TORSO = PartId("torso")
private val LEFT = PartId("leg-l")
private val RIGHT = PartId("leg-r")
private val PAW = PartId("paw-l")
private val PAIR = PairId("legs")

private fun ref(id: String) = TemplateRef(TemplateId(id), 1)

private fun document(): SquashDocument {
    val start = SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("p"), 1)))
    return AddParts(
        listOf(
            PartInstance(TORSO, ref("torso")),
            PartInstance(LEFT, ref("leg"), SocketRef(TORSO, "leg"), pair = PAIR, side = Side.LEFT),
            PartInstance(RIGHT, ref("leg"), SocketRef(TORSO, "leg"), pair = PAIR, side = Side.RIGHT),
            PartInstance(PAW, ref("paw"), SocketRef(LEFT, "paw")),
        ),
    ).apply(start).document
}

private val SquashDocument.pose get() = character.pose

class PoseCommandTest {
    @Test
    fun aBoneTurnsAndItsUndoRestsIt() {
        val start = document()
        scenario(start) {
            run(SetBoneAngle(TORSO, "waist", 12f))
            expect { assertEquals(BonePose(TORSO, "waist", degrees = 12f), it.pose.of(TORSO, "waist")) }
            undo()
            expect(start)
        }
    }

    @Test
    fun aStretchKeepsTheBonesAngle() {
        val start = document()
        scenario(start) {
            run(SetBoneAngle(LEFT, "knee", 40f))
            run(SetBoneStretch(LEFT, "ankle", 1.3f))
            run(SetBoneStretch(LEFT, "knee", 1.1f))
            expect { assertEquals(BonePose(LEFT, "knee", 40f, 1.1f), it.pose.of(LEFT, "knee")) }
            expect { assertEquals(BonePose(LEFT, "ankle", stretch = 1.3f), it.pose.of(LEFT, "ankle")) }
            undo()
            undo()
            undo()
            expect(start)
        }
    }

    @Test
    fun bothSidesWritesTheTwinTheSameAngleAndOneUndoTakesBoth() {
        val start = document()
        scenario(start) {
            run(SetBoneAngle(LEFT, "hip", 30f, bothSides = true))
            expect {
                assertEquals(30f, it.pose.of(LEFT, "hip")?.degrees)
                assertEquals(30f, it.pose.of(RIGHT, "hip")?.degrees)
            }
            run(SetBoneStretch(RIGHT, "ankle", 1.2f, bothSides = true))
            expect { assertEquals(1.2f, it.pose.of(LEFT, "ankle")?.stretch) }
            undo()
            undo()
            expect(start)
        }
    }

    @Test
    fun oneSideAloneLeavesTheTwin() {
        val applied = SetBoneAngle(LEFT, "hip", 30f).apply(document()).document
        assertEquals(null, applied.pose.of(RIGHT, "hip"))
    }

    @Test
    fun aDragOnABoneIsOneUndoStep() {
        val start = document()
        scenario(start) {
            gesture(SetBoneAngle(LEFT, "knee", 10f, bothSides = true), SetBoneAngle(LEFT, "knee", 25f, bothSides = true), SetBoneAngle(LEFT, "knee", 60f, bothSides = true))
            expect { assertEquals(60f, it.pose.of(RIGHT, "knee")?.degrees) }
            assertEquals(1, history.steps.value.done.size)
            undo()
            expect(start)
        }
    }

    @Test
    fun aPawIsPlantedAndFreed() {
        val start = document()
        scenario(start) {
            run(SetPlanted(PAW, true))
            expect { assertEquals(setOf(PAW), it.pose.planted) }
            run(SetPlanted(PAW, false))
            expect { assertEquals(emptySet(), it.pose.planted) }
            undo()
            undo()
            expect(start)
        }
    }

    @Test
    fun resetBoneRestsOneBoneOrBothSides() {
        val posed = listOf(SetBoneAngle(LEFT, "hip", 30f, bothSides = true), SetBoneAngle(LEFT, "knee", 50f)).fold(document()) { doc, command -> command.apply(doc).document }
        scenario(posed) {
            run(ResetBone(RIGHT, "hip"))
            expect { assertEquals(null, it.pose.of(RIGHT, "hip")) }
            expect { assertEquals(30f, it.pose.of(LEFT, "hip")?.degrees) }
            run(ResetBone(LEFT, "hip", bothSides = true))
            expect { assertEquals(listOf(BonePose(LEFT, "knee", 50f)), it.pose.bones) }
            undo()
            undo()
            expect(posed)
        }
    }

    @Test
    fun resetPoseRestsEveryBoneAndFreesEveryPaw() {
        val posed = listOf(SetBoneAngle(LEFT, "hip", 30f, bothSides = true), SetPlanted(PAW, true)).fold(document()) { doc, command -> command.apply(doc).document }
        scenario(posed) {
            run(ResetPose)
            expect { assertEquals(Pose(), it.pose) }
            undo()
            expect(posed)
        }
    }

    @Test
    fun aPoseAtRestNamesNoMovedBone() {
        assertTrue(Pose().isRest)
        assertTrue(Pose(listOf(BonePose(LEFT, "hip")), setOf(PAW)).isRest)
        assertEquals(false, Pose(listOf(BonePose(LEFT, "hip", 1f))).isRest)
    }

    @Test
    fun poseCommandsAndThePoseRoundTripThroughJson() {
        val commands: List<DocumentCommand> = listOf(
            SetBoneAngle(LEFT, "hip", 30f, bothSides = true),
            SetBoneStretch(LEFT, "ankle", 1.2f),
            ResetBone(LEFT, "knee"),
            SetPlanted(PAW, true),
            ResetPose,
            SetPose(Pose(listOf(BonePose(TORSO, "waist", 5f)), setOf(PAW))),
        )
        for (command in commands) {
            val text = Json.encodeToString(DocumentCommand.serializer(), command)
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        }
        val posed = commands.take(4).fold(document()) { doc, command -> command.apply(doc).document }
        assertEquals(posed, readDocument(Json.encodeToString(SquashDocument.serializer(), posed)))
    }
}
