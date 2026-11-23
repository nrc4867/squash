package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.HeldDress
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.surface.PosedCreases
import dev.chieppa.squash.character.surface.garmentPiece
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

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

private enum class SkinSource { RIG, SURFACE, DRESS, PAINT, SKIN, KEYS }

/**
 * The session's one surface in the shown pose: its [skin], made again when the rig or the surface changes, and the surface [posed] by
 * it with its paint, rerun when the pose changes. Both are nodes of the session's graph, so its `settled` covers them; the session
 * shows [posed] as its posed surface. While no pane draws them, the coarse surface of a drag is neither skinned nor posed: both hold none
 * until the full surface follows the drag's end. While a pane does, the coarse surface is posed without looking for skin the pose
 * pushes through a garment ([PosedHiding.kept]). With the character's creases on, the posed surface carries them too, read on the skin
 * once ([CreaseRig]) and in each pose ([PoseCreasing]), the garments' folds raised before what they hide is found; a change of the
 * creases' settings poses again and builds nothing. With pose keys, the posed surface and garments take the keys' shapes by the pose's
 * weights ([keys], [HeldKeys.correction]). While [PoseReport.sink] is set, each posing's [PoseBreakdown] goes to it.
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

    private val keyBuilder = PoseKeyBuilder(session.templates)

    /** How many keys the keys' last build made again rather than kept. */
    val keysBuilt: Int get() = keyBuilder.built

    /**
     * The character's pose keys as shapes on its one rest surface ([PoseKeyBuilder]), made again when the skin or what the keys read of
     * the character changes, and never for the coarse surface of a drag; a key none of whose reads an edit changed is kept, not built.
     */
    val keys: Node<*, HeldKeys> = graph.gather(
        session.document.map { doc -> doc.character.takeIf { it.poseKeys.isNotEmpty() }?.let(::keyedCharacter) }.distinctUntilChanged(),
        { mapOf(SkinSource.SKIN to skinSource) },
        cacheSize = KEPT_BUILDS,
    ) { gathered ->
        val character = gathered.shape ?: return@gather HeldKeys.NONE
        val held = gathered.values.getValue(SkinSource.SKIN) as HeldSkin
        if (held.coarse) HeldKeys.NONE else keyBuilder.build(character, held)
    }

    /**
     * The last keys [keys] settled on, none before the first: what the posed surface reads, so a pose never waits for a key being built
     * but shows the last shapes carried onto the new surface ([HeldKeys.onto]) and takes the new ones when they come.
     */
    private val keysSource = graph.node<HeldKeys?, Any>(keys.output, cacheSize = KEPT_BUILDS) { it ?: HeldKeys.NONE }

    /** The last keys carried onto a surface, which a pose of the same keys on the same surface keeps. */
    @Volatile
    private var lastCarried: Triple<HeldKeys, CharacterSurface, HeldKeys?>? = null

    private fun keysOn(keys: HeldKeys, surface: CharacterSurface): HeldKeys? =
        if (keys.surface === surface) keys
        else lastCarried?.takeIf { it.first === keys && it.second === surface }?.third ?: keys.onto(surface).also { lastCarried = Triple(keys, surface, it) }

    /** Where each garment takes the body's key correction from, kept while the body's keys are the same. */
    @Volatile
    private var lastGarmentKeys: Pair<HeldKeys, GarmentCorrections>? = null

    private fun garmentKeysOf(keys: HeldKeys, body: CharacterSurface, moved: IntArray): GarmentCorrections =
        lastGarmentKeys?.takeIf { it.first === keys }?.second ?: GarmentCorrections(body, moved).also { lastGarmentKeys = keys to it }

    private val paintSource = graph.follow<HeldPaint, Any>(session.sessionSurface.shownPaint, cacheSize = KEPT_BUILDS) { it }

    /** The last posed surface made with its paint laid on its surface, which a run whose paint is still another surface's keeps. */
    @Volatile
    private var lastPosed: HeldPosedSurface? = null

    /** The crease rig read on the last skin creased, which a pose on the same skin keeps. */
    @Volatile
    private var lastCreaseRig: CreaseRig? = null

    /** The implicit skins of the last skin refitted, which a pose on the same skin keeps. */
    @Volatile
    private var lastFits: ImplicitSkins? = null

    private fun fitsOf(held: HeldSkin): ImplicitSkins? =
        if (!ImplicitSkinning.on || held.coarse) null else lastFits?.takeIf { it.held === held } ?: ImplicitSkins(held).also { lastFits = it }

    /** What hiding lowers at rest on the last dress hidden, which a pose wearing the same dress keeps. */
    @Volatile
    private var lastRestHiding: RestHiding? = null

    private suspend fun restHidingOf(rest: CharacterSurface, dress: SurfaceDress): RestHiding =
        lastRestHiding?.takeIf { it.dress === dress } ?: RestHiding.of(rest, dress, IN_PARALLEL).also { lastRestHiding = it }

    private fun creaseRigOf(held: HeldSkin): CreaseRig = lastCreaseRig?.takeIf { it.skin === held } ?: CreaseRig.of(held, lastCreaseRig).also { lastCreaseRig = it }

    private val shaped = combine(
        pose,
        session.document.map { CreaseSettings.of(it.character) }.distinctUntilChanged(),
        session.document.map { it.character.poseKeys.isNotEmpty() }.distinctUntilChanged(),
    ) { shown, creases, keyed -> PoseShape(shown, creases, keyed) }
        .distinctUntilChanged()

    val posed: Node<*, HeldPosedSurface> = graph.gather(
        shaped,
        { shape -> mapOf(SkinSource.SKIN to skinSource, SkinSource.PAINT to paintSource) + if (shape.keyed) mapOf(SkinSource.KEYS to keysSource) else emptyMap() },
        cacheSize = KEPT_BUILDS,
    ) { gathered ->
        val held = gathered.values.getValue(SkinSource.SKIN) as HeldSkin
        val laid = gathered.values.getValue(SkinSource.PAINT) as HeldPaint
        val paint = laid.paint
        val surface = held.surface
        val skin = held.skin
        if (surface == null || skin == null) return@gather HeldPosedSurface(null)
        if (!laid.isOn(surface)) return@gather lastPosed ?: HeldPosedSurface(null)
        val breakdown = PoseReport.start()
        val began = breakdown?.let { TimeSource.Monotonic.markNow() }
        val frames = boneFrames(held.rig, gathered.shape.pose)
        val settings = gathered.shape.creases
        val creasing = if (settings.creases.on && !held.coarse) PoseCreasing(creaseRigOf(held), frames, settings) else null
        val fits = fitsOf(held)
        val keys = (gathered.values[SkinSource.KEYS] as HeldKeys?)?.takeIf { !held.coarse && !it.isEmpty }?.let { keysOn(it, surface) }
        val correction = keys?.let { breakdown.time("key weights") { it.correction(gathered.shape.pose) } }
        val garmentKeys = correction?.let { made -> garmentKeysOf(keys, surface, made.moved).also { it.prepare(held.dress?.garments.orEmpty().map { g -> g.surface }) } }
        val bodyBreakdown = breakdown?.let { PoseBreakdown() }
        val body = posedSurface(surface, skin, paint, frames, IN_PARALLEL, breakdown = bodyBreakdown, fit = fits?.of(0, frames), correction = correction)
        bodyBreakdown?.let { breakdown?.merged(it, "body ") }
        val garmentCreasing = held.dress?.garments.orEmpty().map { creasing?.garment(it) }
        val worn = held.dress?.let { dress ->
            val moved = coroutineScope {
                dress.garments.mapIndexed { i, garment ->
                    async {
                        val own = breakdown?.let { PoseBreakdown() }
                        val posed = own.time("all") {
                            posedSurface(
                                garment.surface, held.garments[i], null, frames, IN_PARALLEL, garmentCreasing[i]?.let { it::shape }, own, fits?.of(i + 1, frames),
                                correction?.let { made -> garmentKeys?.of(garment.surface, made) },
                            )
                        }
                        val hung = own.time("hang") { garment.movedTo(hangingPushed(garment, posed.surface, body.surface), posed.carry) }
                        own?.count("vertices", garment.surface.mesh.vertexCount.toLong())
                        hung to own
                    }
                }.awaitAll()
            }
            moved.forEachIndexed { i, (_, own) -> own?.let { breakdown?.merged(it, "garment ${dress.garments[i].cover.garment.id.value} ") } }
            val atRest = if (held.coarse) null else breakdown.time("hiding at rest") { restHidingOf(surface, dress) }
            val hiding = PosedHiding(body.surface, surface, moved.map { it.first }, dress, atRest)
            breakdown.time("hiding") { if (held.coarse) hiding.kept(body.paint) else hiding.of(body.paint, IN_PARALLEL) }
        }
        val creases = breakdown.time("creases") { creasing?.let { poseCreasing ->
            val byPiece = held.dress?.garments.orEmpty().zip(garmentCreasing).mapNotNull { (garment, creased) -> creased?.let { garment.cover.garmentPiece to it } }.toMap()
            val dressed = worn?.second
            coroutineScope {
                val bodyMarks = async { poseCreasing.body(body.surface, dressed?.shown) }
                val garmentMarks = dressed?.garments.orEmpty().mapNotNull { garment ->
                    byPiece[garment.cover.garmentPiece]?.let { creased -> async { garment.cover.garmentPiece to creased.marks(garment) } }
                }
                PosedCreases(bodyMarks.await(), garmentMarks.awaitAll().toMap())
            }
        } }
        if (breakdown != null && began != null) {
            breakdown.count("body vertices", surface.mesh.vertexCount.toLong())
            breakdown.add("node", began.elapsedNow().inWholeNanoseconds)
            PoseReport.sink?.invoke(breakdown)
        }
        HeldPosedSurface(PosedSurface(body.surface, worn?.first ?: body.paint, worn?.second, body.carry, creases), held.coarse).also { lastPosed = it }
    }

    init {
        session.sessionSurface.showPosed(posed)
    }
}

/** What the posed surface is shown in: the [pose], the [creases] it carries, and whether pose keys reshape it, [keyed]. */
private data class PoseShape(val pose: Pose, val creases: CreaseSettings, val keyed: Boolean)

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
