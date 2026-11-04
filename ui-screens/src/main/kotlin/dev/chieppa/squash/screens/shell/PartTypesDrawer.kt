package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.stretch.frame.DrawerPosition
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Card
import dev.chieppa.stretch.ui.Grid

private val TILE = 88.dp
private val TILE_ICON = 32.dp

/** The starter set as tiles, one per loaded template. Choosing one finds it a socket. */
@Composable
fun PartTypesDrawerContent(state: AppState, position: DrawerPosition) {
    val picked by state.pickedType.collectAsState()
    val document = state.session.collectAsState().value?.document?.collectAsState()?.value
    val parts = document?.let { state.templates.plan(it.character.bodyPlan) }?.templateOrder(state.templates) ?: state.templates.parts
    val tile: @Composable (PartTemplate) -> Unit = { template ->
        PartTile(template, template.id == picked) { state.chooseType(template.id) }
    }
    when (position) {
        DrawerPosition.Bottom -> Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { parts.forEach { tile(it) } }
        DrawerPosition.Side, DrawerPosition.Window -> Box(Modifier.padding(12.dp)) {
            Grid(minCell = TILE, gap = 8.dp) { parts.forEach { tile(it) } }
        }
    }
}

@Composable
private fun PartTile(template: PartTemplate, selected: Boolean, onClick: () -> Unit) {
    val c = Stretch.colors
    Card(Modifier.size(TILE).testTag(ShellTags.pickTile(template.id.value)), onClick, selected) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (hasTileArt(template.id.value)) {
                    PartTileArt(template.id.value, Modifier.fillMaxSize())
                } else {
                    Icon(SquashIcons.Parts, c.inkControl, size = TILE_ICON)
                }
            }
            BasicText(template.name, style = Stretch.type.bodySm.merge(TextStyle(color = c.ink)), maxLines = 1)
        }
    }
}
