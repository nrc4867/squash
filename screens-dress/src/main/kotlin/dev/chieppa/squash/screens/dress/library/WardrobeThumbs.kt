package dev.chieppa.squash.screens.dress.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.WardrobeLook
import dev.chieppa.squash.screens.dress.WardrobeLooks
import dev.chieppa.squash.screens.dress.WardrobeShot
import dev.chieppa.squash.screens.dress.WardrobeThumb
import dev.chieppa.squash.screens.dress.WardrobeThumbnails
import dev.chieppa.squash.screens.dress.wardrobeShots
import dev.chieppa.stretch.theme.Stretch

/** The tiles' thumbnails, and what a tile's art reports of itself: its size in pixels, which thumbnails render at, and whether it is on screen. */
internal class WardrobeThumbs(private val thumbnails: WardrobeThumbnails, private val held: Map<WardrobeShot, WardrobeThumb>, private val sized: (WardrobeShot, IntSize) -> Unit) {
    fun art(shot: WardrobeShot): ImageBitmap? = held[shot]?.image

    /** The modifier for [shot]'s art, which reports its size and whether any of it shows in the window. */
    fun place(shot: WardrobeShot): Modifier = Modifier.onGloballyPositioned { layout ->
        sized(shot, layout.size)
        val shown = layout.boundsInWindow()
        thumbnails.showing(shot, shown.width > 0f && shown.height > 0f)
    }
}

/**
 * Each tile's thumbnail, rendered at the size of its kind's largest tile art, which a tile whose name is open for typing does not
 * shrink, and kept current for [session] while the caller is composed.
 */
@Composable
internal fun rememberWardrobeThumbs(state: AppState, session: CharacterSession): WardrobeThumbs {
    val c = Stretch.colors
    val density = LocalDensity.current.density
    val sizes = remember(session) { HashMap<WardrobeShot, IntSize>() }
    var outfitSize by remember { mutableStateOf<IntSize?>(null) }
    var garmentSize by remember { mutableStateOf<IntSize?>(null) }
    val looks = remember(c.surfacePanel, c.ground, density, outfitSize, garmentSize) {
        WardrobeLooks(outfitSize?.let { look(it, c.surfacePanel, c.ground, density) }, garmentSize?.let { look(it, c.surfacePanel, c.ground, density) })
    }
    LaunchedEffect(session, looks) { state.wardrobeThumbnails.follow(session, looks) }
    val held by state.wardrobeThumbnails.thumbs.collectAsState()
    val shots = wardrobeShots(session.document.collectAsState().value.character.wardrobe).toSet()
    sizes.keys.retainAll(shots)
    return WardrobeThumbs(state.wardrobeThumbnails, held) { shot, size ->
        if (size.width <= 0 || size.height <= 0 || sizes[shot] == size) return@WardrobeThumbs
        sizes[shot] = size
        val largest = sizes.filterKeys { it::class == shot::class }.values.maxBy { it.width.toLong() * it.height }
        when (shot) {
            is WardrobeShot.OfOutfit -> if (outfitSize != largest) outfitSize = largest
            is WardrobeShot.OfGarment -> if (garmentSize != largest) garmentSize = largest
        }
    }
}

private fun look(size: IntSize, background: Color, ground: Color, density: Float) = WardrobeLook(size.width, size.height, background, ground, density)
