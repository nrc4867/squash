package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_bare_slots
import dev.chieppa.squash.uidesign.generated.dress_new
import dev.chieppa.squash.uidesign.generated.dress_new_outfit
import dev.chieppa.squash.uidesign.generated.dress_new_slot
import dev.chieppa.squash.uidesign.generated.dress_outfits
import dev.chieppa.squash.uidesign.generated.dress_slot_empty
import dev.chieppa.squash.uidesign.generated.dress_slot_pick
import dev.chieppa.squash.uidesign.generated.dress_slots
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Card
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Label
import dev.chieppa.stretch.ui.Tooltip
import dev.chieppa.stretch.ui.VerticalDivider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val BAND_HEIGHT = 128.dp
private val HEADER_HEIGHT = 28.dp
private val INSET = 12.dp
private val TILE = 76.dp
private val GAP = 6.dp
private val TILE_PAD = 6.dp
private val TILE_ICON = 24.dp
private const val DASH = 4f
private const val EDGE = 2f

/**
 * The band under the view while Dress is up: the outfits, the shown one ringed, a press showing another, and New; then the shown
 * outfit's slots, each with what it wears in its tooltip, an empty one warn-dashed, a press opening its popover, and New slot.
 */
@Composable
fun DressBand(state: AppState, modifier: Modifier = Modifier) {
    val open by state.session.collectAsState()
    val session = open ?: return
    val document by session.document.collectAsState()
    val wardrobe = document.character.wardrobe
    val shown = wardrobe.shown?.let(wardrobe::outfit)
    val openSlot by state.dress.openSlot.collectAsState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().background(Stretch.colors.surfacePanel).testTag(ShellTags.DRESS_BAND)) {
        Divider()
        Row(Modifier.height(BAND_HEIGHT)) {
            Column {
                Header(stringResource(Res.string.dress_outfits))
                Strip {
                    wardrobe.outfits.forEach { outfit ->
                        Tile(outfit.name, SquashIcons.Garment, outfit.id == wardrobe.shown, DressTags.outfit(outfit.id)) {
                            if (outfit.id != wardrobe.shown) session.send(ShowOutfit(outfit.id))
                        }
                    }
                    Tile(stringResource(Res.string.dress_new), SquashIcons.AddPart, false, DressTags.NEW_OUTFIT, dashed = true, tooltip = stringResource(Res.string.dress_new_outfit)) {
                        scope.launch { state.newOutfit() }
                    }
                }
            }
            VerticalDivider()
            Column(Modifier.weight(1f)) {
                Header(shown?.let { stringResource(Res.string.dress_slots, it.name) } ?: stringResource(Res.string.dress_bare_slots))
                Strip {
                    wardrobe.slots.forEach { slot ->
                        val worn = shown?.picks?.get(slot.id)?.let(wardrobe::garment)
                        val tooltip = if (worn != null) stringResource(Res.string.dress_slot_pick, slot.name, worn.name) else stringResource(Res.string.dress_slot_empty, slot.name)
                        Box {
                            Tile(slot.name, SquashIcons.Slots, slot.id == openSlot, DressTags.slot(slot.id), warn = shown != null && worn == null, tooltip = tooltip) {
                                state.dress.openSlot.value = slot.id
                            }
                            if (slot.id == openSlot) SlotPopover(state, session, document.character, slot.id)
                        }
                    }
                    Tile(stringResource(Res.string.dress_new_slot), SquashIcons.AddSlot, false, DressTags.NEW_SLOT, dashed = true) {
                        scope.launch { state.newSlot() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    Row(Modifier.height(HEADER_HEIGHT).padding(horizontal = INSET), verticalAlignment = Alignment.CenterVertically) {
        Label(title, Stretch.type.bodySm, Stretch.colors.inkMuted)
    }
}

@Composable
private fun Strip(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = GAP), horizontalArrangement = Arrangement.spacedBy(GAP)) { content() }
}

/** A band tile: an icon over a name, ringed when [selected], dashed for an action, warn-dashed for an empty slot. */
@Composable
internal fun Tile(
    name: String,
    icon: ImageVector?,
    selected: Boolean,
    tag: String,
    dashed: Boolean = false,
    warn: Boolean = false,
    tooltip: String = name,
    size: androidx.compose.ui.unit.Dp = TILE,
    onClick: () -> Unit,
) {
    val c = Stretch.colors
    Tooltip(tooltip, emptyList()) {
        Card(Modifier.size(size).testTag(tag), onClick, selected) {
            Box(Modifier.fillMaxSize().background(if (warn) c.warnWash else Color.Transparent)) {
                if (dashed || warn) {
                    val edge = if (warn) c.warnEdge else c.lineStrong
                    Canvas(Modifier.fillMaxSize()) { drawRect(edge, style = Stroke(EDGE, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)))) }
                }
                Column(Modifier.fillMaxSize().padding(TILE_PAD), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    if (icon != null) Icon(icon, if (warn) c.warnInk else c.inkMuted, size = TILE_ICON)
                    BasicText(name, style = Stretch.type.bodySm.merge(TextStyle(color = if (warn) c.warnInk else c.ink)), maxLines = 1)
                }
            }
        }
    }
}
