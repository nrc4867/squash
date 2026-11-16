package dev.chieppa.squash.screens.shell.library

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.isGeneric
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.character.catalog.isDrawnByHand
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.shell.PartTileArt
import dev.chieppa.squash.screens.shell.hasTileArt
import dev.chieppa.squash.screens.shell.jointCount
import dev.chieppa.squash.screens.shell.viewLabel
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.library_tile_tip
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

/** A Library tile's height, art and name. */
internal val TILE_HEIGHT = 72.dp
private val TILE_PAD = 6.dp
private val TILE_ICON = 28.dp

/** How faint a tile a pick cannot take shows. */
internal const val DIM = 0.38f

private const val VIEW_JOIN = ", "

/** A template's tile: its art over its name, faint unless [lit]; its tooltip names it, the views a person draws and its joints. */
@Composable
internal fun PartTile(template: PartTemplate, lit: Boolean) {
    val c = Stretch.colors
    val views = VIEW_ORDER.filter { template.views[it]?.isDrawnByHand() == true }.map { viewLabel(it) }.joinToString(VIEW_JOIN)
    Tooltip(stringResource(Res.string.library_tile_tip, template.name, views, template.jointCount())) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT)
                .alpha(if (lit) 1f else DIM)
                .border(Stretch.stroke, c.linePanel, Stretch.shapes.rowShape)
                .padding(TILE_PAD),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val role = template.role
                if (role != null && hasTileArt(role)) PartTileArt(role, Modifier.fillMaxSize()) else Icon(tileIcon(template), c.inkControl, size = TILE_ICON)
            }
            BasicText(template.name, style = Stretch.type.bodySm.merge(TextStyle(color = c.ink)), maxLines = 1)
        }
    }
}

/** The icon a template with no art of its own shows: a generic sheet, blob or tube by its shape, else the general parts icon. */
private fun tileIcon(template: PartTemplate): ImageVector = when {
    !template.isGeneric -> SquashIcons.Parts
    template.isSheet -> SquashIcons.PartSheet
    template.openEnd == null -> SquashIcons.PartBlob
    else -> SquashIcons.PartTube
}
