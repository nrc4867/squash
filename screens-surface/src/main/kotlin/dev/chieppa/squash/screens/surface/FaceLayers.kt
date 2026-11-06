package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.INK_SLOT
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_layer_clips
import dev.chieppa.squash.uidesign.generated.face_layer_drawing
import dev.chieppa.squash.uidesign.generated.face_layer_drawn
import dev.chieppa.squash.uidesign.generated.face_layer_empty
import dev.chieppa.squash.uidesign.generated.face_layer_fill
import dev.chieppa.squash.uidesign.generated.face_layers
import dev.chieppa.squash.uidesign.generated.face_role_brow
import dev.chieppa.squash.uidesign.generated.face_role_corners
import dev.chieppa.squash.uidesign.generated.face_role_highlight
import dev.chieppa.squash.uidesign.generated.face_role_inner
import dev.chieppa.squash.uidesign.generated.face_role_iris
import dev.chieppa.squash.uidesign.generated.face_role_lashes
import dev.chieppa.squash.uidesign.generated.face_role_lid
import dev.chieppa.squash.uidesign.generated.face_role_lip_line
import dev.chieppa.squash.uidesign.generated.face_role_pupil
import dev.chieppa.squash.uidesign.generated.face_role_teeth
import dev.chieppa.squash.uidesign.generated.face_role_tongue
import dev.chieppa.squash.uidesign.generated.face_role_white
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.theme.Stretch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val LAYER_DOT = 12.dp
private val LAYER_GAP = 8.dp

private val ROLE_NAMES: Map<MarkRole, StringResource> = mapOf(
    MarkRole.LID to Res.string.face_role_lid,
    MarkRole.WHITE to Res.string.face_role_white,
    MarkRole.IRIS to Res.string.face_role_iris,
    MarkRole.PUPIL to Res.string.face_role_pupil,
    MarkRole.HIGHLIGHT to Res.string.face_role_highlight,
    MarkRole.BROW to Res.string.face_role_brow,
    MarkRole.LASHES to Res.string.face_role_lashes,
    MarkRole.LIP_LINE to Res.string.face_role_lip_line,
    MarkRole.INNER to Res.string.face_role_inner,
    MarkRole.TEETH to Res.string.face_role_teeth,
    MarkRole.TONGUE to Res.string.face_role_tongue,
    MarkRole.CORNERS to Res.string.face_role_corners,
)

/**
 * The picked feature's roles as layers, the one drawn last on top: each row picks the role Draw mark and Edit curves work in, and says whether it
 * is empty, being drawn, clipping others, filled or drawn. A filled role's swatch opens the palette for its fill.
 */
@Composable
internal fun LayersSection(state: AppState, session: CharacterSession, character: Character, picked: PickedGroup, group: MarkGroup) {
    val shown = state.face.role
    val clipping = group.clips.map { it.by }.toSet()
    DrawerSection(stringResource(Res.string.face_layers)) {
        group.roles.asReversed().forEach { held ->
            val role = held.role
            val drawn = character.markIn(group, role, picked.part) != null
            val status = when {
                !drawn -> Res.string.face_layer_empty
                role == shown -> Res.string.face_layer_drawing
                role in clipping -> Res.string.face_layer_clips
                held.fill != null -> Res.string.face_layer_fill
                else -> Res.string.face_layer_drawn
            }
            val name = stringResource(ROLE_NAMES.getValue(role))
            ListRow(role == shown, { state.face.role = role }, Modifier.testTag(FaceTags.role(role))) {
                if (role in CLOSED_ROLES && role != MarkRole.LID) {
                    SlotChoice(character.palette, held.fill, name, FaceTags.roleFill(role), { FaceTags.fillSlot(it.value) }, { slot ->
                        session.send(SetMarkGroup(picked.part, group.id, group.withRole(held.copy(fill = slot))))
                    })
                } else {
                    LayerDot(if (drawn) character.palette.colorOf(INK_SLOT) else null)
                }
                Spacer(Modifier.width(LAYER_GAP))
                RowText(name, Modifier.weight(1f))
                RowText(stringResource(status), subtle = true)
            }
        }
    }
}

/** A role's dot: filled in [color] when it draws, hollow while it is empty. */
@Composable
private fun LayerDot(color: Color?) {
    val edge = Stretch.colors.lineStrong
    Box(Modifier.size(LAYER_DOT).background(color ?: Color.Transparent, CircleShape).border(Stretch.stroke, edge, CircleShape))
}

private fun MarkGroup.withRole(role: GroupRole) = copy(roles = roles.map { if (it.role == role.role) role else it })
