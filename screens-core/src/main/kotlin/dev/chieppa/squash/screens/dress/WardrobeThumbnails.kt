package dev.chieppa.squash.screens.dress

import androidx.compose.ui.graphics.ImageBitmap
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** A rendered thumbnail and what it was rendered from. */
class WardrobeThumb(val scene: WardrobeScene, val look: WardrobeLook, val image: ImageBitmap)

/**
 * The outfit and garment thumbnails of the open character, rendered by [artist] on [dispatcher] and kept by what they draw: a
 * thumbnail renders again only when its own [WardrobeScene] changes, or its tile's [WardrobeLook] stops being one it [serves][WardrobeLook.serves],
 * and shows its last image until then. Tiles on screen render before the rest. Rendering waits for the session's graph to settle with
 * no gesture open, and an edit drops the render under way, so a thumbnail never holds up the character's own build. With no [artist]
 * nothing renders.
 */
class WardrobeThumbnails(private val artist: WardrobeArtist?, private val dispatcher: CoroutineDispatcher) {
    private val held = MutableStateFlow<Map<WardrobeShot, WardrobeThumb>>(emptyMap())
    private val onScreen = MutableStateFlow<Set<WardrobeShot>>(emptySet())
    private val working = MutableStateFlow(false)
    private var shownFor: CharacterSession? = null

    /** The thumbnails rendered so far, by tile. */
    val thumbs: StateFlow<Map<WardrobeShot, WardrobeThumb>> = held.asStateFlow()

    /** Whether thumbnails of a settled document are being rendered: true from the start of a round until every tile of it has its own. */
    val rendering: StateFlow<Boolean> = working.asStateFlow()

    /** Says whether [shot]'s tile is on screen. */
    fun showing(shot: WardrobeShot, shown: Boolean) {
        onScreen.update { if (shown) it + shot else it - shot }
    }

    /** Keeps [session]'s thumbnails current in [looks] until cancelled; a session other than the last one starts with none. */
    suspend fun follow(session: CharacterSession, looks: WardrobeLooks) {
        val artist = artist ?: return
        if (shownFor !== session) {
            shownFor = session
            held.value = emptyMap()
        }
        combine(session.document, session.graph.settled, session.history.steps) { document, settled, _ ->
            document.takeIf { settled && !session.history.inGesture }
        }.distinctUntilChanged().collectLatest { document ->
            if (document != null) {
                working.value = true
                refresh(artist, session.templates, document, looks)
                working.value = false
            }
        }
    }

    private suspend fun refresh(artist: WardrobeArtist, templates: TemplateLibrary, document: SquashDocument, looks: WardrobeLooks) {
        val scenes = wardrobeShots(document.character.wardrobe).mapNotNull { shot -> wardrobeScene(document, shot)?.let { shot to it } }.toMap()
        held.update { kept -> kept.filterKeys { it in scenes } }
        val pending = scenes.filterTo(LinkedHashMap()) { (shot, scene) ->
            val look = looks.of(shot) ?: return@filterTo false
            val kept = held.value[shot]
            kept == null || kept.scene != scene || !kept.look.serves(look)
        }
        while (pending.isNotEmpty()) {
            val shot = pending.keys.firstOrNull { it in onScreen.value } ?: pending.keys.first()
            val scene = checkNotNull(pending.remove(shot))
            val look = checkNotNull(looks.of(shot))
            val image = withContext(dispatcher) { artist.render(scene, templates, look) } ?: continue
            held.update { it + (shot to WardrobeThumb(scene, look, image)) }
        }
    }
}
