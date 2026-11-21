package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.chieppa.squash.character.surface.LayerTint
import dev.chieppa.squash.character.surface.TintTarget
import dev.chieppa.squash.document.Palette

/** [color] taken by [tint], its target read from [palette] with [ink] the ink's colour and [skinShadow] the skin's shadow tone; [color] itself with none. */
fun tinted(color: Color, tint: LayerTint?, palette: Palette, ink: Color, skinShadow: Color): Color {
    tint ?: return color
    return lerp(color, targetColor(tint.toward, palette, ink, skinShadow), tint.share.coerceIn(0f, 1f))
}

private fun targetColor(target: TintTarget, palette: Palette, ink: Color, skinShadow: Color): Color = when (target) {
    is TintTarget.Slot -> palette.colorOf(target.name) ?: targetColor(target.orElse, palette, ink, skinShadow)
    TintTarget.Ink -> ink
    TintTarget.White -> Color.White
    TintTarget.SkinShadow -> skinShadow
    TintTarget.LidShade -> LID_SHADE
}

/** The cool grey lilac a lid's shadow on an eye's white is drawn toward. */
private val LID_SHADE = Color(red = 0.56f, green = 0.53f, blue = 0.68f)
