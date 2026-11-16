package dev.chieppa.squash.screens.dress.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons

/** How a tile of Library's Garments half shows. */
internal enum class TileLook {
    /** A garment or outfit as it is. */
    PLAIN,

    /** Worn by the shown outfit, or the outfit shown: ringed with a check. */
    ON,

    /** A garment in no slot: warn-edged, its art dashed. */
    LOOSE,

    /** New, as a dashed action. */
    NEW,

    /** New in a slot nothing fills: warn-dashed. */
    NEW_EMPTY,
}

private val TILE_HEIGHT = 72.dp
private val TILE_PAD = 6.dp
private val TILE_ICON = 24.dp
private val CHECK = 12.dp
private val RING = 2.dp
private const val DASH = 5f
private const val FAINT = 0.38f

/**
 * A tile of Library's Garments half: [icon] over [caption], drawn by [look]. A press goes to the grid the
 * tile sits in; the tile itself takes no focus, which stays with the grid. [caption] can be a field while the name is being typed.
 */
@Composable
internal fun WardrobeTile(
    icon: ImageVector,
    look: TileLook,
    faint: Boolean = false,
    caption: @Composable () -> Unit,
) {
    val c = Stretch.colors
    val shape = Stretch.shapes.rowShape
    val warn = look == TileLook.LOOSE || look == TileLook.NEW_EMPTY
    val dashed = look == TileLook.NEW || look == TileLook.NEW_EMPTY
    val edge = when (look) {
        TileLook.ON -> c.ringSelected
        TileLook.LOOSE, TileLook.NEW_EMPTY -> c.warnEdge
        else -> c.linePanel
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TILE_HEIGHT)
            .alpha(if (faint) FAINT else 1f)
            .background(if (warn) c.warnWash else Color.Transparent, shape)
            .then(
                if (dashed) {
                    Modifier.drawBehind {
                        drawRoundRect(edge, cornerRadius = CornerRadius(RING.toPx() * 3), style = Stroke(RING.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))))
                    }
                } else {
                    Modifier.border(if (look == TileLook.ON) RING else Stretch.stroke, edge, shape)
                },
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(TILE_PAD),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Icon(icon, if (warn) c.warnInk else c.inkMuted, size = TILE_ICON)
            }
            caption()
        }
        if (look == TileLook.ON) Box(Modifier.align(Alignment.TopEnd).padding(TILE_PAD)) { Icon(StretchIcons.Check, c.ringSelected, size = CHECK) }
    }
}

/** A tile's name, in warn ink on a warn tile. */
@Composable
internal fun TileCaption(text: String, warn: Boolean = false) {
    val c = Stretch.colors
    BasicText(text, style = Stretch.type.bodySm.merge(TextStyle(color = if (warn) c.warnInk else c.ink)), maxLines = 1)
}
