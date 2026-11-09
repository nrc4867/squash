package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.Stage

/** Whether the panes show the character in its pose or at rest, outside the Pose stage, which always shows the pose. View state, never saved. */
enum class PoseView { POSED, REST }

/**
 * Whether a pane shows the character in its pose in [stage] with [view] set: [locked] for a pane on a locked Side, Top or Front camera,
 * Single snapped to one among them. In Pose every pane does; with [PoseView.REST] no other pane does; with [PoseView.POSED] every pane
 * does in Rig, and outside Rig Full and Single's free camera do while the locked panes stay at rest to draw on.
 */
fun posesPane(stage: Stage, view: PoseView, locked: Boolean): Boolean = when {
    stage == Stage.POSE -> true
    view == PoseView.REST -> false
    stage == Stage.RIG -> true
    else -> !locked
}

/** Whether the pane [sight] looks through shows the character in its pose, by [posesPane]: a [FullView] is free, any other camera locked. */
fun AppState.showsPose(sight: SurfaceView): Boolean = posesPane(stage.value, poseView.value, locked = sight !is FullView)

/** Whether any pane shows the pose now, which is whether a free camera does. */
fun AppState.posesAnyPane(): Boolean = posesPane(stage.value, poseView.value, locked = false)

/** The open character as the pane [sight] looks through shows it: in its pose where [showsPose], else at rest. */
fun AppState.bodyThrough(sight: SurfaceView): AssembledCharacter? =
    session.value?.let { if (showsPose(sight)) it.posedBody else it.assembly }?.output?.value
