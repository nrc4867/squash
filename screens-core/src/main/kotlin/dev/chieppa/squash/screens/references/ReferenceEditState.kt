package dev.chieppa.squash.screens.references

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.View

/**
 * What the reference drawers remember: the board image [picked], whether a click on it [picks] a color, the view whose trace the
 * References drawer shows, whether that trace shows its [handles] in the panes, which it does while the drawer shows it unlocked, and whether
 * moving it snaps an image's bottom to the ground.
 */
@Stable
class ReferenceEditState {
    var picked by mutableStateOf<ImageId?>(null)
    var picks by mutableStateOf(false)
    var view by mutableStateOf(View.SIDE)
    var snapGround by mutableStateOf(true)
    var handles by mutableStateOf(false)

    fun clear() {
        picked = null
        picks = false
    }
}
