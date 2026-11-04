package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.stretch.theme.Stretch

/** The part fill the board draws until a character has colors of its own. */
val PART_BASE = Color(0xFFE9A24B)
private val PART_SHADE = Color(0xFFC27F35)
val PART_INK = Color(0xFF1C1517)

/** The colors and pixel widths of the panes' drawing. */
data class SheetLook(
    val well: Color,
    val divider: Color,
    val guide: Color,
    val template: Color,
    val active: Color,
    val ring: Color,
    val ground: Color,
    val part: PartMaterial,
    val guideWidth: Float,
    val guideDash: Float,
    val templateWidth: Float,
    val templateDash: Float,
    val templateGap: Float,
    val activeWidth: Float,
    val ringWidth: Float,
    val ringDash: Float,
    val ringGap: Float,
    val lockedOutline: Float,
    val fullOutline: Float,
    val ghostOutline: Float = lockedOutline,
    val dotFill: Color = well,
    val dotRadius: Float = 5f,
    val pickedDotRadius: Float = 8f,
    val dotEdge: Float = 2f,
) {
    /** The side of a pair away from the viewer, in the shade the boards give it. */
    val farPart: PartMaterial = part.copy(base = part.shade)

    val groundMaterial: PartMaterial = PartMaterial(ground)
}

/** The panes' drawing in the current theme and pixel density. */
@Composable
fun rememberSheetLook(): SheetLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return SheetLook(
        well = c.surfaceWell,
        divider = c.linePanel,
        guide = c.inkMuted,
        template = c.inkMuted,
        active = c.accent,
        ring = c.ringSelected,
        ground = c.ground,
        part = PartMaterial(PART_BASE, PART_SHADE, PART_INK),
        guideWidth = px(2),
        guideDash = px(8),
        templateWidth = px(3),
        templateDash = px(10),
        templateGap = px(8),
        activeWidth = px(2),
        ringWidth = px(3),
        ringDash = px(8),
        ringGap = px(6),
        lockedOutline = px(4),
        fullOutline = px(3),
        ghostOutline = px(2),
        dotFill = c.surfaceFloat,
        dotRadius = px(5),
        pickedDotRadius = px(8),
        dotEdge = px(2),
    )
}
