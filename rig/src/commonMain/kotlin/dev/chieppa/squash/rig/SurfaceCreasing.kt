package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.CreaseMarks
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round

/** How high a joint crease's fold stands on cloth, as a share of the joint's radius at full strength. */
private const val CREASE_FOLD = 0.07f

/** How high a wrinkle stands on cloth, in the garment's cells at full strength. */
private const val WRINKLE_FOLD_CELLS = 0.6f

/** The envelope below which a fold shows no band, so a faint one leaves nothing. */
private const val ENVELOPE_FROM = 0.35f

/** Where a fold's ink starts in its envelope, and how far over that it reaches full weight: ink only on the strongest folds. */
private const val FOLD_INK_FROM = 0.7f
private const val FOLD_INK_RANGE = 0.3f

/** How wide a fold's band is at a full envelope, in radians of its wave past its crease: a sliver beside its ink. */
private const val FOLD_WIDTH = 0.8f

/** How much tight cloth wrinkles where it bunches and gathers above a hem, against fully loose cloth: jeans fold at a bent knee and bunch above their cuffs too. */
private const val TIGHT_WRINKLES = 0.85f

/** How strong a joint's shade band is on skin and on cloth, so a band shows only at the crease's core: on skin only at a deep bend. */
private const val SKIN_SHADE = 0.85f
private const val CLOTH_SHADE = 1f

/** How heavy skin's and cloth's crease ink is, against the crease rule's weight: skin subtle, cloth's folds reading at a whole figure's size. */
private const val SKIN_INK = 0.7f
private const val CLOTH_INK = 2f

/** The strength past which a crease draws a shorter line either side of it: on cloth much sooner, as the folds beside it. */
private const val SKIN_SIDES_FROM = 0.6f
private const val CLOTH_SIDES_FROM = 0.3f


/** The fewest vertices an island of band keeps, so no band shows as a speck. */
private const val LEAST_BAND = 8

private const val TURN = 2f * PI.toFloat()

/** A garment's own strengths of its creases, wrinkles and drape. */
data class ClothStrengths(val creases: Float, val wrinkles: Float, val drape: Float) {
    /** Each strength times [by]. */
    operator fun times(by: Float) = ClothStrengths(creases * by, wrinkles * by, drape * by)
}

/** What a pose's creases read from the document: the character's [creases] and each garment's own strengths, [garments]. */
data class CreaseSettings(val creases: CharacterCreases, val garments: Map<GarmentId, ClothStrengths> = emptyMap()) {
    companion object {
        /** [character]'s settings. */
        fun of(character: Character): CreaseSettings = CreaseSettings(
            character.creases,
            if (character.creases.cloth > 0f) character.wardrobe.garments.associate { it.id to ClothStrengths(it.creases, it.wrinkles, it.drape) } else emptyMap(),
        )
    }
}

/**
 * What creasing reads once a skin: [skin]'s crease joints, their reach over its body, hair left out, and for each garment it wears, by
 * its surface, its [ClothPattern] and the joints' reach over it.
 */
class CreaseRig(val skin: HeldSkin, val joints: List<CreaseJoint>, val body: CreaseReach?, val garments: Map<CharacterSurface, GarmentRest>) {
    /** One garment's creasing read at rest. */
    class GarmentRest(val pattern: ClothPattern, val reach: CreaseReach)

    companion object {
        /**
         * What [skin] creases with; nothing for a skin without its surface. What [last] read on the same rig and surfaces is kept: an
         * edit that leaves the rig, the body and a garment's surface as they were reads none of them again.
         */
        fun of(skin: HeldSkin, last: CreaseRig? = null): CreaseRig {
            val surface = skin.surface
            val weights = skin.skin
            if (surface == null || weights == null) return CreaseRig(skin, emptyList(), null, emptyMap())
            val same = last?.takeIf { it.skin.rig === skin.rig && it.skin.surface === surface && it.body != null }
            val creasable = if (same == null) skinCreasable(surface) else null
            val joints = same?.joints ?: creaseJoints(skin.rig, surface.positions, weights, surface.mesh.cell, creasable)
            val body = same?.body ?: CreaseReach.of(joints, surface.render, weights, creasable)
            val garments = skin.dress?.garments.orEmpty().withIndex().mapNotNull { (i, garment) ->
                val garmentSkin = skin.garments.getOrNull(i) ?: return@mapNotNull null
                garment.surface to (same?.garments?.get(garment.surface) ?: GarmentRest(ClothPattern.of(skin.rig, garment, garmentSkin, surface.mesh.cell), CreaseReach.of(joints, garment.surface.render, garmentSkin)))
            }.toMap()
            return CreaseRig(skin, joints, body, garments)
        }
    }
}

/** The creases of one pose, [frames], on what [rig] creases, as [settings] has them. */
class PoseCreasing(private val rig: CreaseRig, private val frames: BoneFrames, private val settings: CreaseSettings) {
    private val creases = settings.creases

    private fun posedJoints(strength: Float, rest: Float, cloth: Boolean = false): List<PosedCreaseJoint?> =
        rig.joints.map { PosedCreaseJoint.of(it, frames, strength, rest, cloth) }

    /**
     * The body's creases on [posed], the body as posed, subtler than cloth's, its ink kept to where [shown] is at least zero, or everywhere
     * with none.
     */
    fun body(posed: CharacterSurface, shown: FloatArray?): CreaseMarks? {
        val reach = rig.body ?: return null
        if (creases.skin <= 0f) return null
        val count = posed.render.vertexCount
        val field = JointCreaseField.of(reach, posedJoints(creases.skin, creases.rest), posed.render.vertices, count, SKIN_SHADE)
        val lines = jointInk(posed.lines, field, SKIN_SIDES_FROM, SKIN_INK) { v -> shown == null || shown.getOrElse(v) { 0f } >= 0f }
        return CreaseMarks(withoutSpecks(field.shade, posed.render.indices, LEAST_BAND), lines)
    }

    /** The creasing of [garment], as worn at rest, or null where cloth creases nowhere. */
    fun garment(garment: SurfaceGarment): GarmentCreasing? {
        if (creases.cloth <= 0f) return null
        val rest = rig.garments[garment.surface] ?: return null
        val own = settings.garments[garment.cover.garment.id] ?: return null
        val joints = posedJoints(creases.cloth * own.creases, creases.rest, cloth = true)
        return GarmentCreasing(garment.surface, rest, joints, own * creases.cloth)
    }
}

/**
 * One garment's creases, wrinkles and drape in a pose: [shape] raises its folds on its posed mesh before it is made a surface, and
 * [marks] reads its shade and ink on the garment as finally worn.
 */
class GarmentCreasing internal constructor(
    private val rest: CharacterSurface,
    private val restRead: CreaseRig.GarmentRest,
    private val joints: List<PosedCreaseJoint?>,
    private val strengths: ClothStrengths,
) {
    private val pattern = restRead.pattern
    private val loose = minOf(1f, pattern.looseness)

    /** How strongly the cloth wrinkles where it bunches: tight cloth a little less than loose. */
    private val tightness = TIGHT_WRINKLES + (1f - TIGHT_WRINKLES) * loose
    private val wrinkling = strengths.wrinkles * tightness

    /** How much the cloth bunches at each drawn vertex in this pose, and its joint creases there, none until [shape] reads them. */
    private var bunching: FloatArray? = null
    private var shaped: JointCreaseField? = null

    /**
     * Raises the garment's folds on its posed drawn mesh's [vertices], and the same corners' [positions]: either side of each joint
     * crease, and on each wrinkle's crest where the cloth bunches, along each vertex's normal.
     */
    fun shape(vertices: FloatArray, positions: FloatArray) {
        val stride = RenderMesh.FLOATS_PER_VERTEX
        val count = vertices.size / stride
        val bunched = clothBunching(rest.mesh.triangles, rest.mesh.vertexCount, rest.positions, positions, count).also { bunching = it }
        val field = JointCreaseField.of(restRead.reach, joints, vertices, count, CLOTH_SHADE, cloth = true).also { shaped = it }
        val corners = positions.size / 3
        for (v in 0 until count) {
            val wave = 0.5f - 0.5f * cos(pattern.wrinkle.getOrElse(v) { 0f })
            val rise = CREASE_FOLD * field.fold[v] + WRINKLE_FOLD_CELLS * pattern.cell * wrinkling * bunched[v] * wave
            if (rise <= 0f) continue
            val at = v * stride
            val normal = vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)
            val moved = vertices.vec(at) + normal * rise
            vertices.put(at, moved)
            if (v < corners) positions.put(v * 3, moved)
        }
    }

    /**
     * The garment's shade and ink as [worn], its ink kept to what of it shows: its joint creases as [shape] read them on the posed cloth
     * before its folds rose, which move it too little to move them, or read on [worn] where the pose left the cloth at rest; each wrinkle
     * and gather a sliver of a lighter band beside its ink, inked only where strongest; no island of either band smaller than
     * [LEAST_BAND] vertices.
     */
    fun marks(worn: SurfaceGarment): CreaseMarks {
        val surface = worn.surface
        val count = surface.render.vertexCount
        val shownValues = worn.shownValues
        val shown = { v: Int -> shownValues.getOrElse(v) { 0f } >= 0f }
        val field = shaped?.takeIf { it.shade.size == count } ?: JointCreaseField.of(restRead.reach, joints, surface.render.vertices, count, CLOTH_SHADE, cloth = true)
        val bunched = bunching
        val wrinkle = foldEnvelope(count) { v -> (bunched?.getOrElse(v) { 0f } ?: 0f) * wrinkling }
        val gather = foldEnvelope(count) { v -> pattern.gather.getOrElse(v) { 0f } * strengths.drape * tightness }
        val folds = FloatArray(count) { v ->
            maxOf(foldShade(pattern.wrinkle, wrinkle, v), foldShade(pattern.gatherPhase, gather, v))
        }
        val lines = surface.lines
        val ink = jointInk(lines, field, CLOTH_SIDES_FROM, CLOTH_INK, shown) +
            crestInk(lines, pattern.wrinkle, foldInk(wrinkle), shown) +
            crestInk(lines, pattern.gatherPhase, foldInk(gather), shown)
        val indices = surface.render.indices
        return CreaseMarks(withoutSpecks(field.shade, indices, LEAST_BAND), ink, withoutSpecks(folds, indices, LEAST_BAND))
    }
}

/** A fold's envelope at each of [count] vertices, eased in from [ENVELOPE_FROM] of [raw] to 1, so a faint fold shows nothing. */
private fun foldEnvelope(count: Int, raw: (Int) -> Float): FloatArray = FloatArray(count) { v -> smoothstep(ENVELOPE_FROM, 1f, raw(v)) }

/**
 * The shade at vertex [v] of a fold of [phase] with [envelope]: a band from its crease, where the phase is a whole number of turns, to
 * [FOLD_WIDTH] times the envelope past it, over a half inside and falling evenly either side, so each fold is one sliver beside its ink
 * that narrows to nothing where its envelope fades.
 */
private fun foldShade(phase: FloatArray, envelope: FloatArray, v: Int): Float {
    val e = envelope[v]
    if (e <= 0f) return 0f
    val p = phase.getOrElse(v) { 0f }
    val d = p - TURN * round(p / TURN)
    val half = FOLD_WIDTH * e * 0.5f
    return (BAND_FROM + (half - abs(d - half)) / FOLD_WIDTH).coerceIn(0f, 1f)
}

/** A fold's ink weight from its [envelope]: only where it is strongest. */
private fun foldInk(envelope: FloatArray): FloatArray = FloatArray(envelope.size) { ((envelope[it] - FOLD_INK_FROM) / FOLD_INK_RANGE).coerceIn(0f, 1f) }
