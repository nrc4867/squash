package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.keys.KeyShape
import dev.chieppa.squash.character.keys.KeyedFields
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import kotlin.math.abs

/** Newton steps a vertex takes onto a key's posed field. */
private const val STEPS = 8

/** A step is cut to this many cells. */
private const val LONGEST_STEP_CELLS = 4f

/** The forward difference a gradient is read over, as a share of the field's cell. */
private const val GRADIENT_SHARE = 0.25f

/** A field difference smaller than this, in body units, moves nothing. */
private const val SAME_LEVEL = 1e-5f

/** How far past a changed member's box a vertex is read at rest, in body units, past its blend. */
private const val CHANGED_REACH = 0.03f

/** What the pose keys read of [character]: all of it but its pose, so posing it keeps the keys' shapes. */
internal fun keyedCharacter(character: Character): Character = character.copy(pose = Pose(), displayPose = Pose())

/**
 * [character]'s pose keys as shapes on its one rest surface, as [held] skins it: for each key, the character's field built with the
 * key's changes read over its parts ([KeyedFields]), and the shape that takes the surface, posed in the key's pose, from the level it
 * lies on in the plain field posed there onto that level in the keyed one, taken back to rest through each vertex's skinning (pose
 * space deformation, Lewis, Cordner and Fong, 2000: a correction made in a pose is stored at rest, so skinning it gives it back there).
 */
suspend fun poseKeyShapes(character: Character, held: HeldSkin, templates: TemplateLibrary): HeldKeys {
    val surface = held.surface ?: return HeldKeys.NONE
    val skin = held.skin ?: return HeldKeys.NONE
    val fields = KeyedFields(character, templates)
    val shapes = character.poseKeys.map { key ->
        val changes = key.changes.filter { !it.isRest }
        val base = fields.base
        if (changes.isEmpty() || base == null) return@map KeyShape.EMPTY
        val keyed = fields.of(changes) ?: return@map KeyShape.EMPTY
        posedKeyShape(key, held.rig, surface, skin, base, keyed, changes.mapTo(HashSet()) { it.part })
    }
    return HeldKeys(surface, character, character.poseKeys, shapes)
}

private suspend fun posedKeyShape(key: PoseKey, rig: Rig, surface: CharacterSurface, skin: SurfaceSkin, base: CharacterField, keyed: CharacterField, changed: Set<PartId>): KeyShape {
    val near = changedMembers(base, changed) + changedMembers(keyed, changed)
    if (near.isEmpty()) return KeyShape.EMPTY
    val frames = boneFrames(rig, Pose(key.pose))
    val posed = posedPositions(surface, skin, rig, frames)
    val h = base.cell * GRADIENT_SHARE
    val longest = base.cell * LONGEST_STEP_CELLS
    val plain = PosedRead(PosedField(SkinSurface.of(surface, rig, base), skin, rig), frames, h)
    val corrected = PosedRead(PosedField(SkinSurface.of(surface, rig, keyed), skin, rig), frames, h)
    val moved = ArrayList<Int>()
    val offsets = ArrayList<Float>()
    val turns = ArrayList<Float>()
    val width = RenderMesh.FLOATS_PER_VERTEX
    val rest = surface.render.vertices
    for (v in 0 until minOf(surface.render.vertexCount, skin.vertexCount)) {
        val at = v * width
        val restPoint = Vec3(rest[at], rest[at + 1], rest[at + 2])
        if (near.none { (member, reach) -> member.boxDistance(restPoint) < reach }) continue
        val from = posed.vec(v * 3)
        val level = plain.value(from)
        if (abs(corrected.value(from) - level) < SAME_LEVEL) continue
        var p = from
        repeat(STEPS) {
            val off = corrected.value(p) - level
            if (abs(off) < SAME_LEVEL) return@repeat
            val g = corrected.gradient(p)
            val g2 = g dot g
            if (g2 < 1e-12f) return@repeat
            var step = g * (-off / g2)
            if (step.length > longest) step = step * (longest / step.length)
            p = p + step
        }
        val shift = p - from
        if (shift.length < SAME_LEVEL) continue
        val back = blendedLinear(skin, v, frames)?.let(::inverseOf) ?: continue
        val offset = back * shift
        val turn = (back * corrected.gradient(p)).unitOr(Vec3.ZERO) - (back * plain.gradient(from)).unitOr(Vec3.ZERO)
        moved += v
        offsets += offset.x; offsets += offset.y; offsets += offset.z
        turns += turn.x; turns += turn.y; turns += turn.z
    }
    return KeyShape(moved.toIntArray(), offsets.toFloatArray(), turns.toFloatArray())
}

/** Every render vertex of [surface] posed by [frames] as the app poses it, linear blend refitted by implicit skinning, three floats each. */
private suspend fun posedPositions(surface: CharacterSurface, skin: SurfaceSkin, rig: Rig, frames: BoneFrames): FloatArray {
    val fit = ImplicitSkin(rig, SkinSurface.of(surface, rig), skin)
    val posed = posedSurface(surface, skin, null, frames, fit = fit)
    val vertices = posed.surface.render.vertices
    val width = RenderMesh.FLOATS_PER_VERTEX
    return FloatArray(posed.surface.render.vertexCount * 3) { vertices[(it / 3) * width + it % 3] }
}

/**
 * A posed field read at one point at a time over all its members, each join blended by the share its members' gradients give there, as
 * implicit skinning reads it, with its gradient.
 */
private class PosedRead(field: PosedField, frames: BoneFrames, h: Float) {
    private val posed = field.posed(frames)
    private val reader = PosedFieldReader(field, h)
    private val all = field.members.indices.toList().toIntArray()

    fun value(p: Vec3): Float = reader.read(posed, p, all, 0, all.size, true)

    fun gradient(p: Vec3): Vec3 {
        reader.read(posed, p, all, 0, all.size, true)
        return reader.gradient
    }
}

/** The linear part of vertex [v]'s linear blend under [frames]: its bones' maps weighed by its weights; null for a vertex no bone holds. */
private fun blendedLinear(skin: SurfaceSkin, v: Int, frames: BoneFrames): Mat3? {
    var x = Vec3.ZERO
    var y = Vec3.ZERO
    var z = Vec3.ZERO
    var total = 0f
    for (slot in 0 until MAX_INFLUENCES) {
        val bone = skin.bones[v * MAX_INFLUENCES + slot]
        if (bone < 0) continue
        val w = skin.weights[v * MAX_INFLUENCES + slot]
        val linear = frames.transforms[bone].linear
        x += linear.x * w
        y += linear.y * w
        z += linear.z * w
        total += w
    }
    if (total <= 0f) return null
    return Mat3(x * (1f / total), y * (1f / total), z * (1f / total))
}

/** The inverse of [m], or null where it is all but singular. */
private fun inverseOf(m: Mat3): Mat3? {
    val det = m.x dot (m.y cross m.z)
    if (abs(det) < 1e-6f) return null
    return Mat3(m.y cross m.z, m.z cross m.x, m.x cross m.y).transposed() * (1f / det)
}

/** The members of [field] the [changed] parts own, each with how far past its box a vertex at rest is read for it. */
private fun changedMembers(field: CharacterField, changed: Set<PartId>): List<Pair<JoinedMember, Float>> =
    field.runs.indices.filter { field.runs[it].partId in changed }.map { i ->
        val member = field.field.members[i]
        member to member.blend + CHANGED_REACH
    }
