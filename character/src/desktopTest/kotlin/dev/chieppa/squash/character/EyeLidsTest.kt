package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.EyeShapes
import dev.chieppa.squash.character.surface.EyeTones
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.EyeLidMark
import dev.chieppa.squash.document.EyeLids
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.toward
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A painted eye's lids: open and unsaved by default, standing where the eye says, and the upper lid's own paint. */
class EyeLidsTest {
    private val eye = PaintedEye("eyes", Anchor(PartId("head"), BODY_PATCH, 0.6f, 0.2f))

    @Test
    fun anEyeWithOpenLidsAndAPlainLookSavesNoLids() {
        val saved = Json.encodeToJsonElement(PaintedEye.serializer(), eye).jsonObject
        assertTrue(saved.keys.none { it in setOf("upperClose", "lowerRise") }, "$saved")
        val look = Json.encodeToJsonElement(EyeLook.serializer(), EyeLook()).jsonObject
        assertTrue("lids" !in look.keys, "$look")
    }

    @Test
    fun openLidsLeaveTheLidLineAsTheOpeningsRim() {
        val open = EyeShapes(EyeLook())
        val stood = EyeShapes(EyeLook(), lidPose = LidPose.OPEN)
        assertEquals(open.lid, stood.lid)
        assertTrue(open.almond.lids.isOpen)
    }

    @Test
    fun shutLidsDrawTheLidLineAlongTheClosedLine() {
        val look = EyeLook()
        val line = assertNotNull(EyeShapes(look, lidPose = LidPose.BLINK).lid)
        val openLine = assertNotNull(EyeShapes(look).lid)
        val top = openLine.maxOf { it.v }
        assertTrue(line.maxOf { it.v } < 0.5f * top, "the top lid has come down")
        val half = line.size / 2
        val over = line.subList(1, half)
        val under = line.subList(half + 1, line.size - 1).reversed()
        over.zip(under).forEach { (a, b) -> assertEquals(a.v, b.v, 1e-4f, "both lids lie on one line at ${a.u}") }
    }

    @Test
    fun aHalfClosedEyeCarriesItsLashOnTheLoweredLid() {
        val look = EyeLook(lashes = 2)
        val open = EyeShapes(look).lashes[1].first()
        val drowsy = EyeShapes(look, lidPose = LidPose(0.5f, 0f)).lashes[1].first()
        assertTrue(drowsy.v < open.v, "a lash's root rides the lid down")
    }

    @Test
    fun aPaintedLidLaysItsColourItsFadeIntoColourAndItsMarks() {
        assertNull(EyeShapes(EyeLook()).detail.lidSkin, "with no slot the lid is the skin")
        val wing = EyeLidMark(out = 0.5f, up = 0.3f)
        val painted = EyeLook(lids = EyeLids(slot = SlotName("shadow"), fadeTo = SlotName("lid"), fade = 0.8f, marks = listOf(wing)))
        val detail = EyeShapes(painted).detail
        assertEquals(0.8f, assertNotNull(detail.lidSkin).fade)
        assertEquals(0f, assertNotNull(detail.lidSkinUnder).fade, "the colour it fades into lies whole under it")
        assertEquals(-0.5f, assertNotNull(detail.lidMarks.single().mark).reachX, "a wing runs toward the back corner")
        assertTrue(detail.lidReach.second > 0f)
        val tones = EyeTones(painted)
        assertEquals(SlotName("shadow"), tones.lidSkin.slot)
        assertEquals(SlotName("lid"), tones.lidSkinUnder.slot)
        assertNull(tones.lidMarks.single().slot, "a mark with no slot is the ink")
    }

    @Test
    fun aFrontLookBlendsTheClosedLine() {
        val half = EyeLook().toward(EyeLook(lids = EyeLids(closedAt = 0.25f, closedBend = 0.5f)), 0.5f)
        assertEquals(0f, half.lids.closedAt, 1e-6f)
        assertEquals(0.25f, half.lids.closedBend, 1e-6f)
    }
}
