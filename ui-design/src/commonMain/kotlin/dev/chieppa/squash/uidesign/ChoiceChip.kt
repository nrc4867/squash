package dev.chieppa.squash.uidesign

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.theme.StretchSizes

private val CHIP_PAD = 8.dp
private val CHIP_GAP = 4.dp
private val CHEVRON = 12.dp
private val DASH = 3.dp
private val DASH_GAP = 2.dp

/** Whether a [ChoiceChip] shows a guess not yet confirmed, as its dashed edge does. */
val Tentative = SemanticsPropertyKey<Boolean>("Tentative")

/**
 * Stretch's chip for a choice: its [text], drawn dashed while it is [tentative], a guess not yet confirmed. With [onClick] it presses
 * and ends in a chevron, as a chip that opens the list of choices does.
 */
@Composable
fun ChoiceChip(
    text: String,
    modifier: Modifier = Modifier,
    tentative: Boolean = false,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Stretch.colors
    val shape = Stretch.shapes.pillShape
    val edge = c.chipEdge
    val stroke = Stretch.stroke
    val outlined = if (tentative) {
        Modifier.drawBehind {
            val w = stroke.toPx()
            drawRoundRect(
                edge,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH_GAP.toPx()))),
            )
        }
    } else {
        Modifier.border(stroke, edge, shape)
    }
    val pressable = if (onClick != null) Modifier.clickable(role = Role.DropdownList, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .height(StretchSizes.chip)
            .clip(shape)
            .then(outlined)
            .then(pressable)
            .semantics {
                if (description != null) contentDescription = description
                set(Tentative, tentative)
            }
            .padding(horizontal = CHIP_PAD),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = Stretch.type.chip.merge(TextStyle(color = c.inkMuted)), maxLines = 1)
        if (onClick != null) Icon(StretchIcons.ChevronDown, c.inkMuted, size = CHEVRON)
    }
}
