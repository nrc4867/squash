package dev.chieppa.squash.screens.shell

import androidx.compose.ui.graphics.ImageBitmap
import dev.chieppa.squash.render.SceneFrame

/** Turns a frame into an image, or null when it cannot. */
fun interface RestPoseArtist {
    suspend fun render(frame: SceneFrame, width: Int, height: Int): ImageBitmap?
}
