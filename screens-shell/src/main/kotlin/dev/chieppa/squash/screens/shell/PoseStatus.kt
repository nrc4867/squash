package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.catalog.partNameOn
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pose_status_curve
import dev.chieppa.squash.uidesign.generated.pose_status_lifted
import dev.chieppa.squash.uidesign.generated.pose_status_planted
import dev.chieppa.squash.uidesign.generated.pose_status_tool
import dev.chieppa.squash.uidesign.generated.rig_side_left
import dev.chieppa.squash.uidesign.generated.rig_side_right
import dev.chieppa.stretch.frame.StatusItem
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** The Pose stage's start of the status bar: the stage and its armed tool, Pose · Drag. */
@Composable
internal fun PoseStatusTool(tool: BuildTool) {
    val label = toolLabel(tool)?.takeIf { tool.poses } ?: return
    Box(Modifier.testTag(ShellTags.STATUS_STAGE)) { StatusItem(stringResource(Res.string.pose_status_tool, stringResource(label))) }
}

/**
 * The Pose stage's end of the status bar: the paw lifted, the picked one when it is free or else the first free one, and how many
 * paws are planted; with every paw planted, only the count; with Body curve armed over a curve, the curve's length and the count.
 * Nothing before the rig has run.
 */
@Composable
internal fun PoseStatusPaws(state: AppState, character: Character) {
    val rig = rememberRig(state) ?: return
    val limbs = state.pose.solverFor(rig).chains.limbs.map { it.id }
    val planted = character.pose.planted.count { it in limbs }
    val free = limbs.filter { it !in character.pose.planted }
    val lifted = state.pose.limb?.takeIf { it in free } ?: free.firstOrNull()
    val curve = character.pose.curve?.takeIf { state.tool.collectAsState().value == BuildTool.BODY_CURVE }
    val text = when {
        curve != null -> stringResource(Res.string.pose_status_curve, (curve.length * PERCENT).roundToInt(), planted)
        lifted == null || planted == 0 -> stringResource(Res.string.pose_status_planted, planted)
        else -> stringResource(Res.string.pose_status_lifted, sidedName(character, lifted, state.templates), planted)
    }
    StatusItem(text)
}

/** A paw named as the Pose stage names its limb: by its type in the place its leg names, with L or R for its side. */
@Composable
private fun sidedName(character: Character, id: PartId, templates: TemplateFolder): String {
    val part = character.part(id) ?: return id.value
    val name = partNameOn(part, part.socket?.parent?.let(character::part), templates)
    return when (part.side) {
        Side.LEFT -> stringResource(Res.string.rig_side_left, name)
        Side.RIGHT -> stringResource(Res.string.rig_side_right, name)
        null -> name
    }
}
