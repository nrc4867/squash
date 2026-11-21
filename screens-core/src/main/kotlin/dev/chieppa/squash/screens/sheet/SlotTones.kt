package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.MaterialLook
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.ShadowShift
import dev.chieppa.squash.document.ShineShape
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.DefaultToneRule
import dev.chieppa.squash.render.Finish
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.ShadeShift
import dev.chieppa.squash.render.Shine
import dev.chieppa.squash.render.Tones
import dev.chieppa.squash.render.shadeShifted

/** How far toward white a rim with no colour of its own lifts its slot's colour. */
private const val RIM_LIFT = 0.55f

/** How far toward white a shine with no colour of its own lifts its slot's colour. */
private const val SHINE_LIFT = 0.85f

/** How much of its colour a band's reflection keeps, each channel alike so its hue holds. */
private const val REFLECTION_SHARE = 0.35f

/**
 * The tones slot [slot] of this palette shows in, [fallback] where the palette holds no such slot: its colour; the shadow its material
 * gives, its own, its shadow slot's, or its colour shifted by its depth and shift; both darkened on a far side ([side]) seen from
 * [toCamera]; and its material's finish. A glow shows its colour alone, on every side. A slot with no material shows as two tones always have.
 */
fun Palette.tonesOf(slot: SlotName?, fallback: Color, shading: ObjectShading, side: BodySide?, toCamera: Vec3): Tones {
    val lit = colorOf(slot) ?: fallback
    val look = slot?.let(::look) ?: MaterialLook()
    val finish = look.finish(lit)
    if (look.glow != null) return Tones(lit, lit, finish)
    val shifted = look.shadowDepth != null || look.shadowShift != ShadowShift.AUTO
    val own = shadowOf(slot) ?: if (shifted) lit.shadeShifted(look.shadowDepth ?: shading.shadowDepth, look.shadowShift.render) else null
    return DefaultToneRule.tones(lit, own, shading, side, toCamera).copy(finish = finish)
}

/** How this material draws past its two tones on a slot of colour [lit]. */
fun MaterialLook.finish(lit: Color): Finish {
    val shineShape = shine?.shape ?: ShineShape.NONE
    return Finish(
        steps = steps,
        rimWidth = rim?.width ?: 0f,
        rim = rim?.color?.compose ?: lit.toward(Color.White, RIM_LIFT),
        shine = when (shineShape) {
            ShineShape.NONE -> Shine.NONE
            ShineShape.SPOT -> Shine.SPOT
            ShineShape.BAND -> Shine.BAND
            ShineShape.STREAK -> Shine.STREAK
        },
        shineSize = if (shineShape == ShineShape.NONE) 0f else shine?.size ?: 0f,
        shineColor = shine?.color?.compose ?: lit.toward(Color.White, SHINE_LIFT),
        reflection = Color(lit.red * REFLECTION_SHARE, lit.green * REFLECTION_SHARE, lit.blue * REFLECTION_SHARE),
        glow = glow?.strength ?: 0f,
        halo = glow?.halo ?: 0f,
    ).let { if (it.copy(rim = Finish.PLAIN.rim, shineColor = Finish.PLAIN.shineColor, reflection = Finish.PLAIN.reflection) == Finish.PLAIN) Finish.PLAIN else it }
}

private val ShadowShift.render: ShadeShift
    get() = when (this) {
        ShadowShift.AUTO -> ShadeShift.AUTO
        ShadowShift.COOL -> ShadeShift.COOL
        ShadowShift.WARM -> ShadeShift.WARM
    }

private val SlotColor.compose: Color get() = Color(red, green, blue)

private fun Color.toward(other: Color, share: Float) =
    Color(red + (other.red - red) * share, green + (other.green - green) * share, blue + (other.blue - blue) * share, alpha)
