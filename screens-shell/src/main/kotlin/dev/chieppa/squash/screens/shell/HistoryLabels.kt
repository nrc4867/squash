package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.uidesign.generated.lines_step_rules
import dev.chieppa.squash.uidesign.generated.lines_step_stroke
import dev.chieppa.squash.uidesign.generated.lines_step_weight
import dev.chieppa.squash.uidesign.generated.move_step
import androidx.compose.runtime.Composable
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.SetBoneTurn
import dev.chieppa.squash.document.SetBoneStretch
import dev.chieppa.squash.document.ResetBone
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.document.SetBoneMode
import dev.chieppa.squash.document.SetKeepVolume
import dev.chieppa.squash.document.SetRiding
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.uidesign.generated.pose_step
import dev.chieppa.squash.uidesign.generated.pose_step_pin
import dev.chieppa.squash.uidesign.generated.pose_step_limits
import dev.chieppa.squash.uidesign.generated.pose_step_volume
import dev.chieppa.squash.uidesign.generated.pose_step_rides
import dev.chieppa.squash.uidesign.generated.pose_step_reset
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BreakPair
import dev.chieppa.squash.document.JoinPair
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.SetTemplate
import dev.chieppa.squash.document.UpdateFromLibrary
import dev.chieppa.squash.uidesign.generated.anatomy_step_pair
import dev.chieppa.squash.uidesign.generated.anatomy_step_template
import dev.chieppa.squash.uidesign.generated.anatomy_update
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.document.SetLineRules
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetLineWeightAmount
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.document.SetGrain
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetSlotLabel
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.AddSeamStrokes
import dev.chieppa.squash.document.PutSeamStrokes
import dev.chieppa.squash.document.ResetSeam
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.character.catalog.placeOf
import dev.chieppa.squash.uidesign.generated.role_step
import dev.chieppa.squash.document.SeamToTwin
import dev.chieppa.squash.document.SetSeamStroke
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.uidesign.generated.rig_step_reach
import dev.chieppa.squash.uidesign.generated.rig_step_reset
import dev.chieppa.squash.uidesign.generated.rig_step_stroke
import dev.chieppa.squash.uidesign.generated.rig_step_strokes
import dev.chieppa.squash.uidesign.generated.rig_step_tidy
import dev.chieppa.squash.uidesign.generated.rig_step_twin
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_step_area
import dev.chieppa.squash.uidesign.generated.color_step_base
import dev.chieppa.squash.uidesign.generated.color_step_palette
import dev.chieppa.squash.uidesign.generated.color_step_region
import dev.chieppa.squash.uidesign.generated.color_step_shading
import dev.chieppa.squash.uidesign.generated.color_step_stroke
import dev.chieppa.squash.uidesign.generated.fur_comb_tool
import dev.chieppa.squash.uidesign.generated.fur_section
import dev.chieppa.squash.uidesign.generated.fur_tool
import dev.chieppa.squash.uidesign.generated.shell_step_add
import dev.chieppa.squash.uidesign.generated.shell_step_break_pair
import dev.chieppa.squash.uidesign.generated.shell_step_outline
import dev.chieppa.squash.uidesign.generated.shell_step_outlines
import dev.chieppa.squash.uidesign.generated.shell_step_parameter
import dev.chieppa.squash.uidesign.generated.shell_step_remove
import dev.chieppa.stretch.core.Command
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

private const val FIRST = "\u0001"
private const val SECOND = "\u0002"
private const val VIEW_JOIN = " · "

/** How the History drawer names each command, in the app's language. */
@Composable
fun historyLabels(): (Command<SquashDocument>) -> String {
    val add = stringResource(Res.string.shell_step_add, FIRST)
    val remove = stringResource(Res.string.shell_step_remove, FIRST)
    val outline = stringResource(Res.string.shell_step_outline, FIRST)
    val outlines = stringResource(Res.string.shell_step_outlines, FIRST)
    val parameter = stringResource(Res.string.shell_step_parameter, FIRST, SECOND)
    val breakPair = stringResource(Res.string.shell_step_break_pair)
    val joinPair = stringResource(Res.string.anatomy_step_pair)
    val retemplated = stringResource(Res.string.anatomy_step_template, FIRST)
    val updated = stringResource(Res.string.anatomy_update)
    val moved = stringResource(Res.string.move_step)
    val palette = stringResource(Res.string.color_step_palette, FIRST)
    val base = stringResource(Res.string.color_step_base, FIRST)
    val area = stringResource(Res.string.color_step_area, FIRST)
    val region = stringResource(Res.string.color_step_region, FIRST)
    val stroke = stringResource(Res.string.color_step_stroke)
    val shading = stringResource(Res.string.color_step_shading)
    val mark = stringResource(Res.string.lines_step_stroke)
    val lineWeight = stringResource(Res.string.lines_step_weight)
    val lineRules = stringResource(Res.string.lines_step_rules)
    val furStrokes = stringResource(Res.string.fur_tool)
    val combed = stringResource(Res.string.fur_comb_tool)
    val furRule = stringResource(Res.string.fur_section)
    val seamStroke = stringResource(Res.string.rig_step_stroke)
    val seamStrokes = stringResource(Res.string.rig_step_strokes)
    val reach = stringResource(Res.string.rig_step_reach, FIRST)
    val tidy = stringResource(Res.string.rig_step_tidy)
    val twin = stringResource(Res.string.rig_step_twin)
    val reset = stringResource(Res.string.rig_step_reset)
    val posed = stringResource(Res.string.pose_step)
    val pinned = stringResource(Res.string.pose_step_pin)
    val limits = stringResource(Res.string.pose_step_limits)
    val volume = stringResource(Res.string.pose_step_volume)
    val rides = stringResource(Res.string.pose_step_rides)
    val resetPose = stringResource(Res.string.pose_step_reset)
    val views = VIEW_ORDER.associateWith { viewLabel(it) }
    val roleStep = stringResource(Res.string.role_step, FIRST)
    val roleNames = roleLabels(fourLegged = false)
    fun label(command: Command<SquashDocument>): String =
        when (command) {
            is Together -> command.commands.firstOrNull()?.let(::label).orEmpty()
            is AddPart -> add.replace(FIRST, partName(command.part))
            is AddParts -> add.replace(FIRST, command.parts.firstOrNull()?.let(::partName).orEmpty())
            is RemovePart -> remove.replace(FIRST, "")
            is BreakPair -> breakPair
            is JoinPair -> joinPair
            is SetTemplate -> retemplated.replace(FIRST, displayName(command.template.id.value))
            is UpdateFromLibrary -> updated
            is SetOutline -> outline.replace(FIRST, views[command.view].orEmpty())
            is SetOutlines -> outlines.replace(FIRST, command.outlines.keys.joinToString(VIEW_JOIN) { views[it].orEmpty() })
            is SetParameters -> moved
            is SetPaletteSlot -> palette.replace(FIRST, displayName(command.name.value))
            is SetSlotLabel -> palette.replace(FIRST, command.label ?: displayName(command.name.value))
            is SetBaseSlot -> base.replace(FIRST, command.slot?.let { displayName(it.value) }.orEmpty())
            is SetAreaFill -> area.replace(FIRST, displayName(command.area.substringAfterLast(":")))
            is SetRegion -> region.replace(FIRST, displayName(command.id.value))
            is SetBrushStroke -> stroke
            is SetCharacterShading -> shading
            is SetMark -> mark
            is SetLineWeight, is SetLineWeightAmount -> lineWeight
            is SetLineRules -> lineRules
            is SetSeamStroke -> seamStroke
            is SetPose, is SetBoneAngle, is SetBoneTurn, is SetBoneStretch, is ResetBone -> posed
            is SetPlanted -> pinned
            is SetBoneMode -> limits
            is SetKeepVolume -> volume
            is SetRiding -> rides
            is ResetPose -> resetPose
            is PutSeamStrokes -> seamStrokes
            is AddSeamStrokes -> tidy
            is SeamToTwin -> twin
            is ResetSeam -> reset
            is SetRole -> roleStep.replace(FIRST, command.role?.let(roleNames).orEmpty())
            is SetParameter -> if (RigParams.isRig(command.name)) reach.replace(FIRST, RigParams.reachJoint(command.name).orEmpty()) else if (PlacementParams.isPlacement(command.name)) moved else parameter.replace(FIRST, displayName(command.name.substringAfterLast(PARAM_SEPARATOR))).replace(SECOND, command.value?.let(::formatNumber).orEmpty())
            is AddMarks -> if (command.marks.any { it.kind == MarkKind.FUR }) furStrokes else ""
            is SetGrain -> combed
            is SetFurRule -> furRule
            else -> ""
        }
    return ::label
}

/** A part's name as its step reads: its type's, led by the place its socket names. */
private fun partName(part: PartInstance): String = displayName(listOfNotNull(placeOf(part.socket?.socket), part.template.id.value).joinToString("-"))

/** What separates a parameter's group from its own name, as in generic.rings. */
private const val PARAM_SEPARATOR = '.'
