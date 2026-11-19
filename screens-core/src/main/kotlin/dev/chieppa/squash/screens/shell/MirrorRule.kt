package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.AppState

/**
 * Whether Mirror means anything now, which enables both its strip button and its key: off Face, where Both, Left and Right choose the eyes;
 * always in Dress, where Paint lays cloth on both sides of the body's middle; with a paired part selected, or with Stroke or Fur armed,
 * which mirror across the middle of the body too; in Shape, with a part down the body's middle selected and a view that shows its left
 * and right active, where a stroke draws half the outline and its mirror image (see [symmetryIn]); in Rig, on a character with a pair,
 * whose seams share their strokes, and in Pose, where a turn or a reset turns the twin the mirrored way.
 */
fun AppState.mirrorApplies(): Boolean {
    if (stage.value == Stage.FACE) return false
    if (stage.value == Stage.DRESS) return true
    if (stage.value == Stage.RIG || stage.value == Stage.POSE) return session.value?.document?.value?.character?.parts?.any { it.pair != null } == true
    val tool = tool.value
    if (tool == BuildTool.SURFACE_STROKE || tool == BuildTool.FUR_STROKE) return true
    val character = session.value?.document?.value?.character ?: return false
    if (selectedPart.value?.let(character::part)?.pair != null) return true
    return stage.value == Stage.SHAPE && symmetryIn(activeView.value) != null
}
