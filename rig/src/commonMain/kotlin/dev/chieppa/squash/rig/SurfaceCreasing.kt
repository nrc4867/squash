package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.CreaseMarks
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.cos
import kotlin.math.pow

/** How high a joint crease's fold stands on cloth, as a share of the joint's radius at full strength. */
private const val CREASE_FOLD = 0.07f

/** How high a wrinkle stands on cloth, in the garment's cells at full strength. */
private const val WRINKLE_FOLD_CELLS = 0.6f

/**
 * How a wave's shade gathers to its crests, as the power its rise from trough to crest is raised to, so a full envelope's band covers
 * about a third of each wave and a weaker one less; and the most an envelope is.
 */
private const val WAVE_SHARPNESS = 3f
private const val WAVE_SHADE = 1.15f
private const val MOST_ENVELOPE = 1f

/** The envelope below which a wave shows nothing, so a faint one leaves no specks. */
private const val ENVELOPE_FROM = 0.4f

/** Where a wave's ink starts in its shade, and how far over that it reaches full weight: ink only where its band shows. */
private const val WAVE_INK_FROM = 0.4f
private const val WAVE_INK_RANGE = 0.4f

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
 * What creasing reads once a skin: [skin]'s crease joints, their reach over its body, and for each garment it wears, by its surface,
 * its [ClothPattern] and the joints' reach over it.
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
            val joints = same?.joints ?: creaseJoints(skin.rig, surface.positions, weights, surface.mesh.cell)
            val body = same?.body ?: CreaseReach.of(joints, surface.render, weights)
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

    private fun posedJoints(strength: Float): List<PosedCreaseJoint?> =
        rig.joints.map { PosedCreaseJoint.of(it, frames, strength, creases.rest) }

    /** The body's creases on [posed], the body as posed, its ink kept to where [shown] is at least zero, or everywhere with none. */
    fun body(posed: CharacterSurface, shown: FloatArray?): CreaseMarks? {
        val reach = rig.body ?: return null
        if (creases.skin <= 0f) return null
        val count = posed.render.vertexCount
        val field = JointCreaseField.of(reach, posedJoints(creases.skin), posed.render.vertices, count)
        val lines = jointInk(posed.lines, field) { v -> shown == null || shown.getOrElse(v) { 0f } >= 0f }
        return CreaseMarks(field.shade, lines)
    }

    /** The creasing of [garment], as worn at rest, or null where cloth creases nowhere. */
    fun garment(garment: SurfaceGarment): GarmentCreasing? {
        if (creases.cloth <= 0f) return null
        val rest = rig.garments[garment.surface] ?: return null
        val own = settings.garments[garment.cover.garment.id] ?: return null
        return GarmentCreasing(garment.surface, rest, posedJoints(creases.cloth * own.creases), own * creases.cloth)
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
    private val loose = pattern.looseness

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
        val field = JointCreaseField.of(restRead.reach, joints, vertices, count).also { shaped = it }
        val wrinkle = strengths.wrinkles * loose
        val corners = positions.size / 3
        for (v in 0 until count) {
            val wave = 0.5f - 0.5f * cos(pattern.wrinkle.getOrElse(v) { 0f })
            val rise = CREASE_FOLD * field.fold[v] + WRINKLE_FOLD_CELLS * pattern.cell * wrinkle * bunched[v] * wave
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
     * before its folds rose, which move it too little to move them, or read on [worn] where the pose left the cloth at rest.
     */
    fun marks(worn: SurfaceGarment): CreaseMarks {
        val surface = worn.surface
        val count = surface.render.vertexCount
        val shownValues = worn.shownValues
        val shown = { v: Int -> shownValues.getOrElse(v) { 0f } >= 0f }
        val field = shaped?.takeIf { it.shade.size == count } ?: JointCreaseField.of(restRead.reach, joints, surface.render.vertices, count)
        val bunched = bunching
        val wrinkle = waveShade(count, pattern.wrinkle) { v -> (bunched?.getOrElse(v) { 0f } ?: 0f) * strengths.wrinkles * loose }
        val drape = waveShade(count, pattern.drapePhase) { v -> pattern.drape.getOrElse(v) { 0f } * strengths.drape * loose }
        val gather = waveShade(count, pattern.gatherPhase) { v -> pattern.gather.getOrElse(v) { 0f } * strengths.drape * loose }
        val shade = FloatArray(count) { v -> maxOf(field.shade[v], wrinkle[v], drape[v], gather[v]) }
        val lines = surface.lines
        val ink = jointInk(lines, field, shown) +
            crestInk(lines, pattern.wrinkle, waveInk(wrinkle), shown) +
            crestInk(lines, pattern.drapePhase, waveInk(drape), shown) +
            crestInk(lines, pattern.gatherPhase, waveInk(gather), shown)
        return CreaseMarks(shade, ink)
    }
}

/** The shade of a wave of [phase] over [count] vertices, its envelope [envelope] at each: a band on each crest, wider as it is stronger. */
private fun waveShade(count: Int, phase: FloatArray, envelope: (Int) -> Float): FloatArray = FloatArray(count) { v ->
    val e = smoothstep(ENVELOPE_FROM, MOST_ENVELOPE, envelope(v))
    if (e <= 0f) 0f else e * (0.5f + 0.5f * cos(phase.getOrElse(v) { 0f })).pow(WAVE_SHARPNESS) * WAVE_SHADE
}

/** A wave's ink weight from its [shade]: where its band shows, rising with it. */
private fun waveInk(shade: FloatArray): FloatArray = FloatArray(shade.size) { ((shade[it] - WAVE_INK_FROM) / WAVE_INK_RANGE).coerceIn(0f, 1f) }
