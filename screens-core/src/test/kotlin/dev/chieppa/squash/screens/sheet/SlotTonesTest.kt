package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.KeyLight
import dev.chieppa.squash.document.MaterialLook
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.ShadowShift
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.look
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.DefaultToneRule
import dev.chieppa.squash.render.Finish
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.Shine
import dev.chieppa.squash.render.ShadeShift
import dev.chieppa.squash.render.shadeShifted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A slot's tones by its material: matte, skin and cloth exactly as a slot with none, the shining presets past them, glow unshaded. */
class SlotTonesTest {
    private val fur = SlotName("fur")
    private val orange = SlotColor(1f, 0.5f, 0f)
    private val shading = ObjectShading(Shading.TWO_TONE)

    private fun palette(material: SlotMaterial?) = Palette(listOf(PaletteSlot(fur, orange, material = material)))

    private fun tones(material: SlotMaterial?, side: BodySide? = null) = palette(material).tonesOf(fur, Color.Gray, shading, side, Vec3(0.3f, 0f, -1f))

    @Test
    fun theDefaultPresetsShowAsASlotWithNoMaterial() {
        val plain = DefaultToneRule.tones(Color(1f, 0.5f, 0f), null, shading, BodySide.NEGATIVE_Z, Vec3(0.3f, 0f, -1f))
        for (preset in listOf(MaterialPreset.MATTE, MaterialPreset.SKIN, MaterialPreset.CLOTH)) {
            assertEquals(plain, tones(SlotMaterial(preset), BodySide.NEGATIVE_Z), preset.name)
        }
        assertEquals(plain, tones(null, BodySide.NEGATIVE_Z))
        assertEquals(Finish.PLAIN, MaterialLook().finish(Color.Red))
    }

    @Test
    fun theShiningPresetsCarryTheirShine() {
        assertEquals(Shine.SPOT, tones(SlotMaterial(MaterialPreset.PLASTIC)).finish.shine)
        val metal = tones(SlotMaterial(MaterialPreset.METAL)).finish
        assertEquals(Shine.BAND, metal.shine)
        assertEquals(3, metal.steps)
        assertTrue(metal.rimWidth > 0f)
        assertEquals(Shine.STREAK, tones(SlotMaterial(MaterialPreset.GLASS)).finish.shine)
    }

    @Test
    fun aGlowShowsItsColourOnEverySide() {
        val glow = tones(SlotMaterial(MaterialPreset.GLOW), BodySide.NEGATIVE_Z)
        assertEquals(Color(1f, 0.5f, 0f), glow.lit)
        assertEquals(glow.lit, glow.shadow)
        assertTrue(glow.finish.glowing)
    }

    @Test
    fun aDepthOrShiftShiftsTheShadowItsWay() {
        val cool = tones(SlotMaterial(shadowShift = ShadowShift.COOL, shadowDepth = 0.4f))
        assertEquals(Color(1f, 0.5f, 0f).shadeShifted(0.4f, ShadeShift.COOL), cool.shadow)
        assertNotEquals(Color(1f, 0.5f, 0f).shadeShifted(0.4f), cool.shadow)
    }

    @Test
    fun aKeyLightReachesTheMaterial() {
        val keyed = CharacterShading(key = KeyLight(0f, 2f, 0f)).objectShading()
        assertEquals(Vec3(0f, 1f, 0f), keyed.material(tones(null), Color.Black, Vec3.Z).key)
        assertEquals(null, shading.material(tones(null), Color.Black, Vec3.Z).key)
        assertEquals(MaterialPreset.METAL.look.shine?.size, tones(SlotMaterial(MaterialPreset.METAL)).finish.shineSize)
    }
}
