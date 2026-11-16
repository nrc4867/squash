package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.HeldDress
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.character.surface.HeldPosedSurface
import dev.chieppa.squash.character.surface.HeldSurface
import dev.chieppa.squash.character.surface.IN_PARALLEL
import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.character.surface.KEPT_BUILDS
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.Volatile

/**
 * A one surface's skin: its weights from [rig], with the [surface] they were read on, and the [dress] it wears with each garment's
 * weights, [garments], read from the body's at the coordinates it grows from; null pieces for a character without one, and while the
 * skin waits for a gesture's end; [coarse] on the coarse surface of a drag.
 */
class HeldSkin(
    val rig: Rig,
    val surface: CharacterSurface?,
    val skin: SurfaceSkin?,
    val dress: SurfaceDress? = null,
    val garments: List<SurfaceSkin> = emptyList(),
    val coarse: Boolean = false,
)

private enum class SkinSource { RIG, SURFACE, DRESS, PAINT, SKIN }

/**
 * The session's one surface in the shown pose: its [skin], made again when the rig or the surface changes, and the surface [posed] by
 * it with its paint, rerun when the pose changes. Both are nodes of the session's graph, so its `settled` covers them; the session
 * shows [posed] as its posed surface. While no pane draws them, the coarse surface of a drag is neither skinned nor posed: both hold none
 * until the full surface follows the drag's end. While a pane does, the coarse surface is posed without looking for skin the pose
 * pushes through a garment ([PosedHiding.kept]).
 */
class SessionSurfacePose(session: CharacterSession, rig: Node<*, Rig>, pose: Flow<Pose>) {
    private val graph = session.graph
    private val garmentSkins = GarmentSkins()
    private val sources: Map<SkinSource, Node<*, Any>> = mapOf(
        SkinSource.RIG to graph.follow<Rig, Any>(rig, cacheSize = KEPT_BUILDS) { it },
        SkinSource.SURFACE to graph.gather<Boolean, SkinSource, HeldSurface, Any>(session.sessionSurface.skinShown, { mapOf(SkinSource.SURFACE to session.characterSurface) }, cacheSize = KEPT_BUILDS) { gathered ->
            val held = gathered.values.getValue(SkinSource.SURFACE)
            if (held.coarse && !gathered.shape) HeldSurface(null, coarse = true) else held
        },
        SkinSource.DRESS to graph.follow<HeldDress, Any>(session.sessionSurface.dress, cacheSize = KEPT_BUILDS) { it },
    )

    /** The last skin made with its dress worn on its surface, which a run whose dress is still the last surface's keeps. */
    @Volatile
    private var lastSkin: HeldSkin? = null

    val skin: Node<*, HeldSkin> = graph.gather(MutableStateFlow(Unit), { sources }, cacheSize = KEPT_BUILDS) { gathered ->
        val held = gathered.values.getValue(SkinSource.RIG) as Rig
        val shown = gathered.values.getValue(SkinSource.SURFACE) as HeldSurface
        val surface = shown.surface ?: return@gather HeldSkin(held, null, null)
        val dress = (gathered.values.getValue(SkinSource.DRESS) as HeldDress).dress
        if (dress?.isOn(surface) == false) return@gather lastSkin ?: HeldSkin(held, null, null)
        val garments = dress?.garments.orEmpty().map { garment -> garmentSkins.of(held, garment, IN_PARALLEL) }
        HeldSkin(held, surface, SurfaceSkin.of(held, surface, IN_PARALLEL), dress, garments, shown.coarse).also { lastSkin = it }
    }

    private val skinSource = graph.follow<HeldSkin, Any>(skin, cacheSize = KEPT_BUILDS) { it }
    private val paintSource = graph.follow<HeldPaint, Any>(session.sessionSurface.shownPaint, cacheSize = KEPT_BUILDS) { it }

    /** The last posed surface made with its paint laid on its surface, which a run whose paint is still another surface's keeps. */
    @Volatile
    private var lastPosed: HeldPosedSurface? = null

    val posed: Node<*, HeldPosedSurface> = graph.gather(
        pose,
        { mapOf(SkinSource.SKIN to skinSource, SkinSource.PAINT to paintSource) },
        cacheSize = KEPT_BUILDS,
    ) { gathered ->
        val held = gathered.values.getValue(SkinSource.SKIN) as HeldSkin
        val laid = gathered.values.getValue(SkinSource.PAINT) as HeldPaint
        val paint = laid.paint
        val surface = held.surface
        val skin = held.skin
        if (surface == null || skin == null) return@gather HeldPosedSurface(null)
        if (!laid.isOn(surface)) return@gather lastPosed ?: HeldPosedSurface(null)
        val frames = boneFrames(held.rig, gathered.shape)
        val body = posedSurface(surface, skin, paint, frames, IN_PARALLEL)
        val worn = held.dress?.let { dress ->
            val moved = coroutineScope {
                dress.garments.mapIndexed { i, garment ->
                    async { garment.movedTo(hangingPushed(garment, posedSurface(garment.surface, held.garments[i], null, frames).surface, body.surface)) }
                }.awaitAll()
            }
            val hiding = PosedHiding(body.surface, surface, moved, dress)
            if (held.coarse) hiding.kept(body.paint) else hiding.of(body.paint, IN_PARALLEL)
        }
        HeldPosedSurface(PosedSurface(body.surface, worn?.first ?: body.paint, worn?.second, body.carry), held.coarse).also { lastPosed = it }
    }

    init {
        session.sessionSurface.showPosed(posed)
    }
}

/** Each garment's weights, kept while its surface and the rig are the same objects, so an edit that leaves a garment whole skins it once. */
class GarmentSkins {
    private var rig: Rig? = null
    private var kept: Map<CharacterSurface, SurfaceSkin> = emptyMap()

    suspend fun of(on: Rig, garment: SurfaceGarment, runAll: RunAll = IN_TURN): SurfaceSkin {
        if (on !== rig) {
            rig = on
            kept = emptyMap()
        }
        return kept[garment.surface] ?: SurfaceSkin.of(on, garment.surface, runAll).also { kept = (kept + (garment.surface to it)).entries.toList().takeLast(KEPT).associate { e -> e.key to e.value } }
    }

    private companion object {
        const val KEPT = 8
    }
}
