package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.Stage

/**
 * Whether the pane [sight] looks through shows the character in its pose: in the Rig stage every pane does; otherwise, outside Single,
 * Full does and in the Pose stage every pane does. Single shows the rest pose outside the Rig stage.
 */
fun AppState.showsPose(sight: SurfaceView): Boolean {
    val stage = stage.value
    return stage == Stage.RIG || (layout.value != SheetLayout.SINGLE && (sight is FullView || stage == Stage.POSE))
}

/** The open character as the pane [sight] looks through shows it: in its pose where [showsPose], else at rest. */
fun AppState.bodyThrough(sight: SurfaceView): AssembledCharacter? =
    session.value?.let { if (showsPose(sight)) it.posedBody else it.assembly }?.output?.value
