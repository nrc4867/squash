package dev.chieppa.squash.screens.references

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReferenceCommandsTest {
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val box = BodyBox(Vec3(-1f, 0f, -0.3f), Vec3(1f, 1.2f, 0.3f))

    private fun withTwo(): SquashDocument {
        val first = newImageCommand(empty, "Side sheet", byteArrayOf(1), 640, 430, ImageId("side"))
        val one = first.apply(empty).document
        return newImageCommand(one, "Front sheet", byteArrayOf(2), 150, 400, ImageId("front")).apply(one).document
    }

    @Test
    fun newImagesLandUnderEachOther() {
        val document = withTwo()
        val (side, front) = document.references.images
        assertEquals(BOARD_GAP, side.board.y)
        assertEquals(side.board.y + BOARD_IMAGE_WIDTH * 430 / 640 + BOARD_GAP, front.board.y)
        assertEquals(150f, front.board.width, "a narrow image keeps its own width")
    }

    @Test
    fun tracingInAnotherViewMovesTheImageThereAndNoneTakesItAway() {
        val document = withTwo()
        val side = assertNotNull(traceInCommand(document, ImageId("side"), View.SIDE, box)).apply(document).document
        assertEquals(ImageId("side"), side.character.traces[View.SIDE]?.image)
        assertNull(traceInCommand(side, ImageId("side"), View.SIDE, box), "already there")
        val front = assertNotNull(traceInCommand(side, ImageId("side"), View.FRONT, box)).apply(side).document
        assertEquals(setOf(View.FRONT), front.character.traces.keys)
        val none = assertNotNull(traceInCommand(front, ImageId("side"), null, box)).apply(front).document
        assertEquals(emptyMap(), none.character.traces)
    }

    @Test
    fun aPickedColorJoinsThePaletteAndTheImage() {
        val document = withTwo()
        val picked = assertNotNull(pickedColorCommand(document, ImageId("side"), SlotColor(0.9f, 0.6f, 0.3f), "Side sheet 1")).apply(document).document
        val slot = picked.character.palette.slots.single { it.name == SlotName("picked-1") }
        assertEquals("Side sheet 1", slot.label)
        assertEquals(listOf(SlotName("picked-1")), picked.references.image(ImageId("side"))?.picked)
        assertEquals(1, pickedCount(picked, ImageId("side")))
    }
}
