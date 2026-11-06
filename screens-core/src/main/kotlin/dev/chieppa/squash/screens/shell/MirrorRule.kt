package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.AppState

/**
 * Whether Mirror means anything now, which enables both its strip button and its key: off Face, where Both, Left and Right choose the eyes, with
 * a paired part selected, or with Stroke or Fur armed, which mirror across the middle of the body too.
 */
fun AppState.mirrorApplies(): Boolean {
    if (stage.value == Stage.FACE) return false
    val tool = tool.value
    if (tool == BuildTool.SURFACE_STROKE || tool == BuildTool.FUR_STROKE) return true
    val character = session.value?.document?.value?.character ?: return false
    return selectedPart.value?.let(character::part)?.pair != null
}
