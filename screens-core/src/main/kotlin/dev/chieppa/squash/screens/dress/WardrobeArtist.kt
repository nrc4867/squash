package dev.chieppa.squash.screens.dress

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.chieppa.squash.character.build.TemplateLibrary
import kotlin.math.abs

/** How far a thumbnail's size may stray from its tile's, as a share of the tile's, before it renders again at the tile's. */
private const val SIZE_SLACK = 0.2f

/**
 * How a wardrobe thumbnail is drawn: [width] by [height] pixels at [density] pixels a density-independent pixel, on [background], the
 * whole character standing on [ground].
 */
data class WardrobeLook(val width: Int, val height: Int, val background: Color, val ground: Color, val density: Float = 1f) {
    /** Whether a thumbnail drawn in this look serves a tile that [wanted] draws: the same colors and density, and near its size. */
    fun serves(wanted: WardrobeLook): Boolean =
        background == wanted.background && ground == wanted.ground && density == wanted.density &&
            abs(width - wanted.width) <= wanted.width * SIZE_SLACK && abs(height - wanted.height) <= wanted.height * SIZE_SLACK
}

/** The looks of outfit tiles and of garment tiles; null for a kind whose tiles have not been laid out, which renders nothing yet. */
data class WardrobeLooks(val outfit: WardrobeLook?, val garment: WardrobeLook?) {
    fun of(shot: WardrobeShot): WardrobeLook? = when (shot) {
        is WardrobeShot.OfOutfit -> outfit
        is WardrobeShot.OfGarment -> garment
    }
}

/** Renders a wardrobe thumbnail offscreen, building [WardrobeScene.document] from [templates]; null when it cannot. */
fun interface WardrobeArtist {
    suspend fun render(scene: WardrobeScene, templates: TemplateLibrary, look: WardrobeLook): ImageBitmap?
}
