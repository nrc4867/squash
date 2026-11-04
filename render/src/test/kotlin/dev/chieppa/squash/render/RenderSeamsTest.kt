package dev.chieppa.squash.render

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.WidthSample
import dev.chieppa.squash.geom.dragProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderSeamsTest {
    private val tapered = assertNotNull(dragProfile(listOf(WidthSample(0.3f, 1f), WidthSample(0.7f, 1f)), fromPlane = false))
    private val straight = List(5) { RibbonPoint(Vec3(it.toFloat(), 0f, 0f), Vec3.Z) }

    @Test
    fun aRibbonTapersToNothingAtItsEndsAndHoldsItsWeightBetween() {
        val ribbon = assertNotNull(StrokeRibbon.along(straight, tapered))
        assertEquals(0f, ribbon.weightAt(0))
        assertEquals(0f, ribbon.weightAt(ribbon.pointCount - 1), 1e-5f)
        assertEquals(1f, ribbon.weightAt(ribbon.pointCount / 2), 1e-3f)
        assertEquals(ribbon.vertices.size / StrokeRibbon.FLOATS_PER_VERTEX, ribbon.pointCount * 2)
        assertTrue(ribbon.indices.max() < ribbon.pointCount * 2)
    }

    @Test
    fun aRibbonNeedsTwoPlaces() {
        assertNull(StrokeRibbon.along(listOf(straight[0], straight[0]), tapered))
    }

    @Test
    fun aRibbonPassesThroughItsPoints() {
        val ribbon = assertNotNull(StrokeRibbon.along(straight, tapered))
        val xs = (0 until ribbon.pointCount).map { ribbon.vertices[it * 2 * StrokeRibbon.FLOATS_PER_VERTEX] }
        for (point in straight) assertTrue(xs.any { kotlin.math.abs(it - point.position.x) < 1e-5f })
    }

    @Test
    fun theFarSideOfAPairDarkensAndSwapsAsTheCameraCrosses() {
        val rule = FarSideDarkening()
        val onPlusZ = Vec3(1f, 0f, 3f)
        assertEquals(1f, rule.shareFor(BodySide.POSITIVE_Z, onPlusZ))
        assertTrue(rule.shareFor(BodySide.NEGATIVE_Z, onPlusZ) < 1f)
        val onMinusZ = Vec3(1f, 0f, -3f)
        assertEquals(1f, rule.shareFor(BodySide.NEGATIVE_Z, onMinusZ))
        assertTrue(rule.shareFor(BodySide.POSITIVE_Z, onMinusZ) < 1f)
        assertEquals(1f, rule.shareFor(null, onMinusZ), "an unpaired part has no far side")
    }

    @Test
    fun farSideDarkeningSwitchesOffAndHeadOnNeitherSideIsFar() {
        val off = FarSideDarkening(enabled = false)
        assertEquals(1f, off.shareFor(BodySide.NEGATIVE_Z, Vec3(0f, 0f, 3f)))
        assertEquals(1f, FarSideDarkening().shareFor(BodySide.NEGATIVE_Z, Vec3(3f, 0f, 0f)))
    }

    @Test
    fun theDefaultChoiceIsFlatAndKeepsAColorAsItIs() {
        assertEquals(Shading.FLAT, ObjectShading().mode)
        val tones = DefaultToneRule.tones(Color(0.8f, 0.6f, 0.4f), null, ObjectShading(), null, Vec3.Z)
        assertEquals(Color(0.8f, 0.6f, 0.4f), tones.lit)
        assertTrue(tones.shadow.red < tones.lit.red)
    }

    @Test
    fun aPaleColorsDefaultShadowWarmsRatherThanTurningGrey() {
        val cream = Color(0.99f, 0.94f, 0.82f)
        val shadow = DefaultToneRule.tones(cream, null, ObjectShading(), null, Vec3.Z).shadow
        assertTrue(shadow.red < cream.red && shadow.blue < cream.blue)
        assertTrue(shadow.red - shadow.blue > 1.5f * (cream.red - cream.blue), "the gap from red to blue grows: $shadow")
        assertTrue(shadow.green > shadow.blue, "it holds toward orange: $shadow")
    }

    @Test
    fun aSlotsOwnShadowIsTheToneAndAFarSideShiftsBothTones() {
        val own = Color(0.5f, 0.3f, 0.2f)
        val color = Color(0.9f, 0.7f, 0.4f)
        assertEquals(own, DefaultToneRule.tones(color, own, ObjectShading(), null, Vec3.Z).shadow)
        val far = DefaultToneRule.tones(color, own, ObjectShading(), BodySide.NEGATIVE_Z, Vec3.Z)
        assertTrue(far.lit.red < color.red && far.shadow.red < own.red)
    }

    @Test
    fun orbitingPastHeadOnTheFarSideTakesNoStep() {
        val rule = FarSideDarkening()
        val shares = (-60..60).map { tenth ->
            val radians = Math.toRadians(tenth / 2.0)
            rule.shareFor(BodySide.NEGATIVE_Z, Vec3(kotlin.math.cos(radians).toFloat(), 0f, kotlin.math.sin(radians).toFloat()))
        }
        assertEquals(1f, shares.first())
        assertEquals(rule.share, rule.shareFor(BodySide.NEGATIVE_Z, Vec3(0f, 0f, 1f)))
        val biggest = shares.zipWithNext { a, b -> kotlin.math.abs(a - b) }.max()
        assertTrue(biggest < 0.015f, "half a degree moves a tone by $biggest")
    }
}
