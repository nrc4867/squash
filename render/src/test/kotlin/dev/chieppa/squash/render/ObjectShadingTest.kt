package dev.chieppa.squash.render

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How close two directions' parts are taken as equal. */
private const val NEAR = 1e-5f

/**
 * Shadow tones keep white white, a cool grey, while a saturated color still shades warm; a fold's band is lighter than a crease's
 * and dark cloth's folds catch a highlight; a view light is the front's for a front view
 * and turns round with the camera, so a back view is lit from the viewer's side, while a body light stays put.
 */
class ObjectShadingTest {
    @Test
    fun whiteShadesToACoolGreyAndASaturatedColorShadesWarm() {
        val white = Color.White.shadeShifted(0.24f)
        assertTrue(white.blue > white.red && white.blue > white.green, "white in shade is cool, not tan: $white")
        assertTrue(white.red < 1f, "and darker")
        val red = Color(0.8f, 0.3f, 0.2f)
        val shaded = red.shadeShifted(0.24f)
        assertTrue(shaded.blue / red.blue < shaded.red / red.red, "a saturated color loses blue fastest: $shaded")
        assertEquals(Color.White, Color.White.shadeShifted(0f))
        val onWhite = creaseTones(Color.White)
        assertTrue(onWhite.lit.blue > onWhite.lit.red, "a crease band on white is cool too")
        assertTrue(onWhite.shadow.red > onWhite.lit.red, "and lighter where it lies in shadow")
        val skin = Color(0.98f, 0.85f, 0.76f)
        val onSkin = creaseTones(skin)
        assertTrue(onSkin.lit.red > onSkin.lit.blue && onSkin.lit.red < 1f, "on skin a crease is a darker, warmer skin tone: ${onSkin.lit}")
    }

    @Test
    fun aFoldIsLighterThanACreaseAndOnlyDarkClothCatchesAHighlight() {
        assertTrue(foldTones(Color.White).lit.red > creaseTones(Color.White).lit.red && foldTones(Color.White).lit.red < 1f, "a fold's band is lighter than a crease's")
        val denim = Color(0.25f, 0.31f, 0.44f)
        assertTrue(denim.isDarkCloth)
        assertTrue(!Color(0.4f, 0.6f, 0.42f).isDarkCloth && !Color.White.isDarkCloth, "mid and pale cloth show their folds by shade alone")
        val lifted = highlightOf(Tones(denim, denim))
        assertTrue(lifted.lit.red > denim.red && lifted.lit.blue > denim.blue, "a highlight lifts toward white: ${lifted.lit}")
    }

    @Test
    fun aViewLightTurnsWithTheCameraAndABodyLightStays() {
        val light = PartMaterial.DEFAULT_TO_LIGHT
        val view = ObjectShading()
        assertVec(light, view.lightFor(Vec3(1f, 0.1f, 0f)), "a front view keeps the front's light")
        val back = view.lightFor(Vec3(-1f, 0.1f, 0f))
        assertVec(Vec3(-light.x, light.y, -light.z), back, "a back view is lit from the viewer's front")
        assertTrue((back dot Vec3(-1f, 0f, 0f)) > 0f, "toward the camera")
        assertVec(light, view.lightFor(Vec3(0f, 1f, 0f)), "a view from straight above keeps the front's light")
        assertVec(light, ObjectShading(light = LightFrame.BODY).lightFor(Vec3(-1f, 0.1f, 0f)), "a body light stays put")
    }

    private fun assertVec(expected: Vec3, actual: Vec3, message: String) {
        assertEquals(expected.x, actual.x, NEAR, message)
        assertEquals(expected.y, actual.y, NEAR, message)
        assertEquals(expected.z, actual.z, NEAR, message)
    }
}
