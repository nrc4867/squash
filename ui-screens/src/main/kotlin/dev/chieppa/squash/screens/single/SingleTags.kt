package dev.chieppa.squash.screens.single

import dev.chieppa.squash.document.View

/** Test tags for the Single layout. */
object SingleTags {
    const val VIEWPORT = "single.viewport"
    const val HUD = "single.hud"
    const val THUMBNAILS = "single.thumbnails"
    const val TOOLBAR = "single.toolbar"

    fun thumbnail(view: View) = "single.thumbnail.${view.name}"
}
