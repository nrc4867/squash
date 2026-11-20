package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.EyeShapes
import dev.chieppa.squash.character.surface.EyeTones
import dev.chieppa.squash.character.surface.TintTarget
import dev.chieppa.squash.document.EyeIrisShade
import dev.chieppa.squash.document.EyeLashClumps
import dev.chieppa.squash.document.EyeLidMass
import dev.chieppa.squash.document.EyeLidShadow
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.toward
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A painted eye's detail: off and unsaved by default, its shapes and tones when on, and blended between a look and its front look. */
class EyeDetailTest {
    private val detailed = EyeLook(
        lidMass = EyeLidMass(weight = 0.3f, flick = 0.2f),
        lashClumps = EyeLashClumps(count = 3),
        irisShade = EyeIrisShade(upper = 0.5f, lower = 0.3f, rim = 0.1f),
        lidShadow = EyeLidShadow(depth = 0.2f),
        iris = SlotName("iris"),
        pupil = SlotName("pupil"),
        white = SlotName("white"),
    )

    @Test
    fun aPlainLookDrawsNoDetailAndSavesNone() {
        val detail = EyeShapes(EyeLook()).detail
        assertNull(detail.lid)
        assertNull(detail.irisUpper)
        assertNull(detail.irisLower)
        assertNull(detail.irisRim)
        assertNull(detail.lidShadow)
        val saved = Json.encodeToJsonElement(EyeLook.serializer(), EyeLook()).jsonObject
        assertTrue(saved.keys.none { it in setOf("lidMass", "lashClumps", "irisShade", "lidShadow", "topFlatness", "outerCorner") }, "$saved")
    }

    @Test
    fun aLidMassTakesThePlaceOfTheLidLine() {
        val shapes = EyeShapes(detailed)
        assertNull(shapes.lid, "no even line round the opening")
        val lid = assertNotNull(shapes.detail.lid)
        assertEquals(3, lid.clumps)
        assertNotNull(EyeShapes(EyeLook()).lid, "a plain look keeps its line")
    }

    @Test
    fun theIrisTonesAreCutsOfTheIrisAndTheShadowOneOfTheOpening() {
        val detail = EyeShapes(detailed).detail
        assertFalse(assertNotNull(detail.irisUpper).outside, "the darker top is kept inside its cut")
        assertTrue(assertNotNull(detail.irisLower).outside, "the crescent outside its")
        assertTrue(assertNotNull(detail.irisRim).outside)
        assertNull(assertNotNull(detail.lidShadow).oval, "the lid shadow is cut by the opening moved down")
    }

    @Test
    fun tonesWithNoSlotsOfTheirOwnAreTheFillsTinted() {
        val tones = EyeTones(detailed)
        assertEquals(SlotName("iris"), tones.irisUpper.slot)
        assertEquals(TintTarget.Slot(SlotName("pupil"), TintTarget.Ink), tones.irisUpper.tint?.toward)
        assertEquals(TintTarget.SkinShadow, tones.lidShadow.tint?.toward)
        assertEquals(TintTarget.Ink, tones.lid.tint?.toward, "a lid mass with no slot and no ink slot is the ink")
        val own = EyeTones(detailed.copy(irisShade = detailed.irisShade.copy(upperSlot = SlotName("deep"))))
        assertEquals(SlotName("deep"), own.irisUpper.slot)
        assertNull(own.irisUpper.tint)
    }

    @Test
    fun aFrontLookBlendsTheDetailAsItDoesTheShape() {
        val half = EyeLook().toward(detailed, 0.5f)
        assertEquals(0.15f, half.lidMass.weight, 1e-6f)
        assertEquals(0.25f, half.irisShade.upper, 1e-6f)
        assertEquals(0.1f, half.lidShadow.depth, 1e-6f)
    }
}
