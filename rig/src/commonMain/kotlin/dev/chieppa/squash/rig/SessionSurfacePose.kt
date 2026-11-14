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
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A one surface's skin: its weights from [rig], with the [surface] they were read on, and the [dress] it wears with each garment's
 * weights, [garments], read from the body's at the coordinates it grows from; null pieces for a character without one.
 */
class HeldSkin(val rig: Rig, val surface: CharacterSurface?, val skin: SurfaceSkin?, val dress: SurfaceDress? = null, val garments: List<SurfaceSkin> = emptyList())

private enum class SkinSource { RIG, SURFACE, DRESS, PAINT, SKIN }

/**
 * The session's one surface in the shown pose: its [skin], made again when the rig or the surface changes, and the surface [posed] by
 * it with its paint, rerun when the pose changes. Both are nodes of the session's graph, so its `settled` covers them; the session
 * shows [posed] as its posed surface.
 */
class SessionSurfacePose(session: CharacterSession, rig: Node<*, Rig>, pose: Flow<Pose>) {
    private val graph = session.graph
    private val garmentSkins = GarmentSkins()

    val skin: Node<*, HeldSkin> = graph.gather(
        MutableStateFlow(Unit),
        {
            mapOf(
                SkinSource.RIG to graph.follow<Rig, Any>(rig, cacheSize = 1) { it },
                SkinSource.SURFACE to graph.follow<HeldSurface, Any>(session.characterSurface, cacheSize = 1) { it },
                SkinSource.DRESS to graph.follow<HeldDress, Any>(session.oneSurface.dress, cacheSize = 1) { it },
            )
        },
        cacheSize = 1,
    ) { gathered ->
        val held = gathered.values.getValue(SkinSource.RIG) as Rig
        val surface = (gathered.values.getValue(SkinSource.SURFACE) as HeldSurface).surface
        val dress = (gathered.values.getValue(SkinSource.DRESS) as HeldDress).dress
        val garments = dress?.garments.orEmpty().map { garment -> garmentSkins.of(held, garment, IN_PARALLEL) }
        HeldSkin(held, surface, surface?.let { SurfaceSkin.of(held, it, IN_PARALLEL) }, dress, garments)
    }

    private val skinSource = graph.follow<HeldSkin, Any>(skin, cacheSize = 1) { it }
    private val paintSource = graph.follow<HeldPaint, Any>(session.oneSurface.shownPaint, cacheSize = 1) { it }

    val posed: Node<*, HeldPosedSurface> = graph.gather(
        pose,
        { mapOf(SkinSource.SKIN to skinSource, SkinSource.PAINT to paintSource) },
        cacheSize = 1,
    ) { gathered ->
        val held = gathered.values.getValue(SkinSource.SKIN) as HeldSkin
        val paint = (gathered.values.getValue(SkinSource.PAINT) as HeldPaint).paint
        val surface = held.surface
        val skin = held.skin
        if (surface == null || skin == null) return@gather HeldPosedSurface(null)
        val frames = boneFrames(held.rig, gathered.shape)
        val body = posedSurface(surface, skin, paint, frames)
        val worn = held.dress?.let { dress ->
            val moved = dress.garments.mapIndexed { i, garment -> garment.movedTo(hangingPushed(garment, posedSurface(garment.surface, held.garments[i], null, frames).surface, body.surface)) }
            PosedHiding(body.surface, surface.render.indices, moved, dress).of(body.paint)
        }
        HeldPosedSurface(PosedSurface(body.surface, worn?.first ?: body.paint, worn?.second, body.carry))
    }

    init {
        session.oneSurface.showPosed(posed)
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
