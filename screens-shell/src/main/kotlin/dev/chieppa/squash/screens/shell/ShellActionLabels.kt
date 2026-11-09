package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.uidesign.generated.sheet_pane_fit
import dev.chieppa.squash.uidesign.generated.sheet_pane_swap
import dev.chieppa.squash.uidesign.generated.move_tool
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_tool_brush
import dev.chieppa.squash.uidesign.generated.color_tool_fill
import dev.chieppa.squash.uidesign.generated.color_tool_pick
import dev.chieppa.squash.uidesign.generated.color_tool_region_border
import dev.chieppa.squash.uidesign.generated.edit_tool_erase
import dev.chieppa.squash.uidesign.generated.draw_half_outline
import dev.chieppa.squash.uidesign.generated.edit_tool_mirror
import dev.chieppa.squash.uidesign.generated.add_part
import dev.chieppa.squash.uidesign.generated.shell_action_close
import dev.chieppa.squash.uidesign.generated.shell_action_commands
import dev.chieppa.squash.uidesign.generated.shell_action_draw
import dev.chieppa.squash.uidesign.generated.shell_action_edit_curves
import dev.chieppa.squash.uidesign.generated.shell_action_new
import dev.chieppa.squash.uidesign.generated.shell_action_open
import dev.chieppa.squash.uidesign.generated.shell_action_redo
import dev.chieppa.squash.uidesign.generated.shell_action_reset_layout
import dev.chieppa.squash.uidesign.generated.shell_action_put_down
import dev.chieppa.squash.uidesign.generated.shell_action_save
import dev.chieppa.squash.uidesign.generated.shell_action_save_as
import dev.chieppa.squash.uidesign.generated.shell_action_start_template
import dev.chieppa.squash.uidesign.generated.shell_action_undo
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_inspector
import dev.chieppa.squash.uidesign.generated.shell_drawer_color
import dev.chieppa.squash.uidesign.generated.shell_drawer_lines
import dev.chieppa.squash.uidesign.generated.stage_face
import dev.chieppa.squash.uidesign.generated.shell_drawer_rig
import dev.chieppa.squash.uidesign.generated.rig_tool_erase
import dev.chieppa.squash.uidesign.generated.rig_tool_paint
import dev.chieppa.squash.uidesign.generated.rig_tool_smooth
import dev.chieppa.squash.uidesign.generated.stage_shape
import dev.chieppa.squash.uidesign.generated.stage_pose
import dev.chieppa.squash.uidesign.generated.stage_dress
import dev.chieppa.squash.uidesign.generated.dress_tool_draw
import dev.chieppa.squash.uidesign.generated.dress_tool_cover
import dev.chieppa.squash.uidesign.generated.dress_tool_loose_end
import dev.chieppa.squash.uidesign.generated.dress_new_garment
import dev.chieppa.squash.uidesign.generated.view_garments_shown
import dev.chieppa.squash.uidesign.generated.view_garments_ghosted
import dev.chieppa.squash.uidesign.generated.view_garments_hidden
import dev.chieppa.squash.uidesign.generated.view_posed
import dev.chieppa.squash.uidesign.generated.view_rest
import dev.chieppa.squash.uidesign.generated.pose_tool_drag
import dev.chieppa.squash.uidesign.generated.pose_tool_turn
import dev.chieppa.squash.uidesign.generated.pose_tool_pin
import dev.chieppa.squash.uidesign.generated.pose_tool_look
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.lines_tool_surface_stroke
import dev.chieppa.squash.uidesign.generated.lines_tool_width
import dev.chieppa.squash.uidesign.generated.face_tool_draw
import dev.chieppa.squash.uidesign.generated.face_tool_gaze
import dev.chieppa.squash.uidesign.generated.fur_comb_tool
import dev.chieppa.squash.uidesign.generated.fur_tool
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.squash.uidesign.generated.spine_tool
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.chieppa.squash.uidesign.generated.pose_tool_body_curve

/** The text of each action, in the strings. */
private val texts: Map<ActionId, StringResource> = mapOf(
    ShellActions.NEW to Res.string.shell_action_new,
    ShellActions.OPEN to Res.string.shell_action_open,
    ShellActions.SAVE to Res.string.shell_action_save,
    ShellActions.SAVE_AS to Res.string.shell_action_save_as,
    ShellActions.CLOSE to Res.string.shell_action_close,
    ShellActions.UNDO to Res.string.shell_action_undo,
    ShellActions.REDO to Res.string.shell_action_redo,
    ShellActions.COMMANDS to Res.string.shell_action_commands,
    ShellActions.PARTS to Res.string.shell_drawer_parts,
    ShellActions.HISTORY to Res.string.shell_drawer_history,
    ShellActions.INSPECTOR to Res.string.shell_drawer_inspector,
    ShellActions.STAGE_SHAPE to Res.string.stage_shape,
    ShellActions.STAGE_COLOR to Res.string.shell_drawer_color,
    ShellActions.STAGE_LINES to Res.string.shell_drawer_lines,
    ShellActions.STAGE_FACE to Res.string.stage_face,
    ShellActions.STAGE_RIG to Res.string.shell_drawer_rig,
    ShellActions.STAGE_DRESS to Res.string.stage_dress,
    ShellActions.STAGE_POSE to Res.string.stage_pose,
    ShellActions.DRAW_GARMENT to Res.string.dress_tool_draw,
    ShellActions.EDIT_GARMENT to Res.string.shell_action_edit_curves,
    ShellActions.ERASE_GARMENT to Res.string.edit_tool_erase,
    ShellActions.SMOOTH_GARMENT to Res.string.smooth_tool,
    ShellActions.COVER to Res.string.dress_tool_cover,
    ShellActions.LOOSE_END to Res.string.dress_tool_loose_end,
    ShellActions.NEW_GARMENT to Res.string.dress_new_garment,
    ShellActions.DRESS_HALF_OUTLINE to Res.string.draw_half_outline,
    ShellActions.GARMENTS_SHOWN to Res.string.view_garments_shown,
    ShellActions.GARMENTS_GHOSTED to Res.string.view_garments_ghosted,
    ShellActions.GARMENTS_HIDDEN to Res.string.view_garments_hidden,
    ShellActions.POSE_POSED to Res.string.view_posed,
    ShellActions.POSE_REST to Res.string.view_rest,
    ShellActions.DRAG to Res.string.pose_tool_drag,
    ShellActions.TURN to Res.string.pose_tool_turn,
    ShellActions.BODY_CURVE to Res.string.pose_tool_body_curve,
    ShellActions.PIN to Res.string.pose_tool_pin,
    ShellActions.LOOK to Res.string.pose_tool_look,
    ShellActions.PAINT_WEIGHT to Res.string.rig_tool_paint,
    ShellActions.ERASE_WEIGHT to Res.string.rig_tool_erase,
    ShellActions.SMOOTH_WEIGHT to Res.string.rig_tool_smooth,
    ShellActions.HELP to Res.string.shell_drawer_help,
    ShellActions.RESET_LAYOUT to Res.string.shell_action_reset_layout,
    ShellActions.DRAW to Res.string.shell_action_draw,
    ShellActions.SPINE_WIDTH to Res.string.spine_tool,
    ShellActions.EDIT_CURVES to Res.string.shell_action_edit_curves,
    ShellActions.ERASE to Res.string.edit_tool_erase,
    ShellActions.SMOOTH to Res.string.smooth_tool,
    ShellActions.MOVE to Res.string.move_tool,
    ShellActions.REGION_BORDER to Res.string.color_tool_region_border,
    ShellActions.FILL to Res.string.color_tool_fill,
    ShellActions.BRUSH to Res.string.color_tool_brush,
    ShellActions.PICK_COLOR to Res.string.color_tool_pick,
    ShellActions.PAINT_ERASE to Res.string.edit_tool_erase,
    ShellActions.LINE_WIDTH to Res.string.lines_tool_width,
    ShellActions.SURFACE_STROKE to Res.string.lines_tool_surface_stroke,
    ShellActions.LINE_EDIT to Res.string.shell_action_edit_curves,
    ShellActions.SURFACE_ERASE to Res.string.edit_tool_erase,
    ShellActions.FUR_STROKE to Res.string.fur_tool,
    ShellActions.COMB to Res.string.fur_comb_tool,
    ShellActions.DRAW_MARK to Res.string.face_tool_draw,
    ShellActions.EDIT_MARK to Res.string.shell_action_edit_curves,
    ShellActions.ERASE_MARK to Res.string.edit_tool_erase,
    ShellActions.GAZE_TARGET to Res.string.face_tool_gaze,
    ShellActions.FIT_PANE to Res.string.sheet_pane_fit,
    ShellActions.PUT_DOWN to Res.string.shell_action_put_down,
    ShellActions.SWAP_PANE to Res.string.sheet_pane_swap,
    ShellActions.MIRROR to Res.string.edit_tool_mirror,
    ShellActions.START_TEMPLATE to Res.string.shell_action_start_template,
    ShellActions.HALF_OUTLINE to Res.string.draw_half_outline,
    ShellActions.ADD_PART to Res.string.add_part,
)

/** The name of the action arming [tool], or null for a tool with none. */
internal fun toolLabel(tool: BuildTool): StringResource? = texts[ActionId(tool.actionId)]

/** The labels for [dev.chieppa.stretch.frame.LocalActionText]. */
@Composable
fun actionText(): (String) -> String {
    val labels = texts.entries.associate { (id, resource) -> id.value to stringResource(resource) }
    return { labels[it] ?: it }
}

/** The icon for an action's icon key. */
fun actionIcon(key: String): ImageVector? = when (key) {
    "file" -> StretchIcons.File
    "folder" -> StretchIcons.Folder
    "save" -> StretchIcons.Save
    "undo" -> StretchIcons.Undo
    "redo" -> StretchIcons.Redo
    "command" -> StretchIcons.Command
    "parts" -> SquashIcons.Parts
    "history" -> StretchIcons.Clock
    "part" -> SquashIcons.Part
    "help" -> StretchIcons.Help
    "pen" -> SquashIcons.DrawOutline
    "spine-and-width" -> SquashIcons.SpineAndWidth
    "edit-curves" -> SquashIcons.EditCurves
    "erase" -> SquashIcons.Erase
    "smooth" -> SquashIcons.SmoothCurve
    "move" -> SquashIcons.Move
    "region-border" -> SquashIcons.RegionBorder
    "fill" -> SquashIcons.Fill
    "brush" -> SquashIcons.Brush
    "pick-color" -> SquashIcons.PickColor
    "line-width" -> SquashIcons.LineWidth
    "surface-stroke" -> SquashIcons.SurfaceStroke
    "fur-stroke" -> SquashIcons.FurStroke
    "comb" -> SquashIcons.Comb
    "draw-mark" -> SquashIcons.DrawMark
    "gaze-target" -> SquashIcons.GazeTarget
    "mirror" -> SquashIcons.Mirror
    "start-template" -> SquashIcons.StartFromTemplate
    "half-outline" -> SquashIcons.HalfOutline
    "add-part" -> SquashIcons.AddPart
    "paint-weight" -> SquashIcons.PaintWeight
    "erase-weight" -> SquashIcons.EraseWeight
    "smooth-weight" -> SquashIcons.SmoothWeight
    "drag" -> SquashIcons.Drag
    "turn" -> SquashIcons.Turn
    "body-curve" -> SquashIcons.BodyCurve
    "pin" -> SquashIcons.Pin
    "look" -> SquashIcons.Look
    "draw-garment" -> SquashIcons.DrawGarment
    "cover" -> SquashIcons.Cover
    "loose-end" -> SquashIcons.LooseEnd
    "new-garment" -> SquashIcons.NewGarment
    else -> null
}

