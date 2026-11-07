package dev.chieppa.squash.screens.surface.rig

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side

/**
 * The Rig stage's part colors: muted and far apart, one for each part of the body, or for each pair, in the order the
 * character first holds them, from the start again past the last. A pair's right side shows its left's color darker.
 */
val RIG_PALETTE: List<Color> = listOf(
    Color(0xFFC9A37C),
    Color(0xFF8FA8C2),
    Color(0xFF9DBB8C),
    Color(0xFFC49AB4),
    Color(0xFFDCB35C),
    Color(0xFF86B3AA),
    Color(0xFFB6A3D6),
    Color(0xFFD49A8C),
    Color(0xFFA9B67A),
    Color(0xFF7FA3B8),
    Color(0xFFC2A08F),
    Color(0xFFA3A0C9),
)

/** How much of its left side's color a pair's right side keeps. */
const val RIGHT_SIDE_SHADE = 0.88f

/** Each part of [character] in its Rig stage color. */
fun rigPartColors(character: Character): Map<PartId, Color> {
    val groups = LinkedHashMap<Any, Int>()
    return character.parts.associate { part ->
        val group = groups.getOrPut(part.pair ?: part.id) { groups.size }
        val color = RIG_PALETTE[group % RIG_PALETTE.size]
        part.id to if (part.pair != null && part.side == Side.RIGHT) color.shaded(RIGHT_SIDE_SHADE) else color
    }
}

private fun Color.shaded(keep: Float) = Color(red * keep, green * keep, blue * keep, alpha)
