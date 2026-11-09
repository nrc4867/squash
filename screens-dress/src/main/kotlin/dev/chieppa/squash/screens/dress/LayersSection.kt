package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SetLayers
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_layers
import dev.chieppa.squash.uidesign.generated.dress_reorder
import dev.chieppa.squash.uidesign.generated.dress_show_garment
import dev.chieppa.squash.uidesign.generated.dress_wins
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val DOT = 8.dp
private const val NAME_JOIN = ", "

/** [order], outer first, with the garment at [from] moved to [to]. */
fun movedLayer(order: List<GarmentId>, from: Int, to: Int): List<GarmentId> {
    if (from == to || from !in order.indices) return order
    val moving = order[from]
    val rest = order - moving
    return rest.take(to.coerceIn(0, rest.size)) + moving + rest.drop(to.coerceIn(0, rest.size))
}

/**
 * The worn garments as layers, outer first: a drag on a row's grip moves it among them in one step, a press picks it, and its eye
 * leaves it out of view or brings it back. Under them, each part two garments cover or change, warn-coloured, with the outer one winning.
 */
@Composable
internal fun LayersSection(state: AppState, session: CharacterSession, character: Character) {
    val worn = character.wardrobe.worn.reversed()
    val picked by state.dress.garment.collectAsState()
    val hidden by state.dress.hidden.collectAsState()
    DrawerSection(stringResource(Res.string.dress_layers)) {
        Box(Modifier.fillMaxWidth().testTag(DressTags.LAYERS)) {
            var rowHeight by remember { mutableIntStateOf(1) }
            androidx.compose.foundation.layout.Column {
                worn.forEachIndexed { index, garment ->
                    Box(Modifier.onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }) {
                        LayerRow(state, character, garment, garment.id == picked, garment.id in hidden) { moved ->
                            val order = movedLayer(worn.map { it.id }, index, index + (moved / rowHeight).roundToInt())
                            if (order != worn.map { it.id }) session.send(SetLayers(order.reversed()))
                        }
                    }
                }
            }
        }
        character.layerClashes().forEach { clash ->
            val wardrobe = character.wardrobe
            val outer = wardrobe.garment(clash.outer) ?: return@forEach
            Row(Modifier.fillMaxWidth().testTag(DressTags.clash(clash.part.id)), verticalAlignment = Alignment.CenterVertically) {
                Dot(outer.color?.let(character.palette::colorOf))
                Spacer(Modifier.size(DOT))
                RowText(partName(clash.part, state.templates), Modifier.weight(1f))
                val under = clash.under.mapNotNull { wardrobe.garment(it)?.name }.joinToString(NAME_JOIN)
                androidx.compose.foundation.text.BasicText(
                    stringResource(Res.string.dress_wins, outer.name, under),
                    style = Stretch.type.value.merge(androidx.compose.ui.text.TextStyle(color = Stretch.colors.warnInk)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** One layer's row: grip, colour, name, slot and the eye; [onDragged] gets how far the grip moved in pixels when it lets go. */
@Composable
private fun LayerRow(state: AppState, character: Character, garment: Garment, picked: Boolean, hidden: Boolean, onDragged: (Float) -> Unit) {
    var moved by remember { mutableFloatStateOf(0f) }
    ListRow(picked, { state.dress.garment.value = garment.id }, Modifier.testTag(DressTags.layer(garment.id))) {
        Box(
            Modifier.testTag(DressTags.layerGrip(garment.id)).pointerInput(garment.id) {
                detectDragGestures(
                    onDragStart = { moved = 0f },
                    onDragEnd = { onDragged(moved) },
                    onDragCancel = { moved = 0f },
                ) { change, amount ->
                    change.consume()
                    moved += amount.y
                }
            },
        ) { Icon(StretchIcons.Drag, Stretch.colors.inkMuted, size = 12.dp, contentDescription = stringResource(Res.string.dress_reorder)) }
        Dot(garment.color?.let(character.palette::colorOf))
        RowText(garment.name, Modifier.weight(1f))
        RowText(garment.slot?.let(character.wardrobe::slot)?.name.orEmpty(), subtle = true)
        IconButton(
            if (hidden) StretchIcons.EyeOff else StretchIcons.Eye,
            stringResource(Res.string.dress_show_garment, garment.name),
            { state.dress.toggleShown(garment.id) },
            Modifier.testTag(DressTags.layerShow(garment.id)),
            on = !hidden,
        )
    }
}

/** A garment's colour as a small dot, hollow with none. */
@Composable
private fun Dot(color: Color?) {
    Box(Modifier.size(DOT).background(color ?: Color.Transparent, CircleShape))
}
