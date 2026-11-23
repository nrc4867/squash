package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.KeptSurfaces
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.keys.KeyShape
import dev.chieppa.squash.character.keys.KeyedFields
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.IN_PARALLEL
import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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


/** Vertices one piece of a key's work takes. */
private const val KEY_CHUNK = 512

/** What the pose keys read of [character]: all of it but its pose, so posing it keeps the keys' shapes. */
internal fun keyedCharacter(character: Character): Character = character.copy(pose = Pose(), displayPose = Pose())

/**
 * Builds a character's pose keys as shapes on its one rest surface, one build at a time, keeping what the next build can use again: the
 * part surfaces and run solids the keys' fields are built from, so an edit builds again only the parts it changed, and each key's shape
 * with what it read ([KeyReads]), so a key none of whose reads an edit changed is carried onto the new surface rather than built again.
 */
class PoseKeyBuilder(private val templates: TemplateLibrary) {
    private val surfaces = KeptSurfaces()
    private val solids = RunSolids()
    private val lock = Mutex()

    /** The last keys built, which the next build takes each unchanged key from. */
    private var last: HeldKeys? = null

    /** The implicit skin of the last skin a key was posed on, which a build on the same skin keeps. */
    private var fit: Pair<HeldSkin, ImplicitSkin>? = null

    /** How many keys the last build made again rather than kept. */
    var built: Int = 0
        private set

    /**
     * [character]'s pose keys as shapes on its one rest surface, as [held] skins it: for each key, the character's field built with the
     * key's changes read over its parts ([KeyedFields]), and the shape that takes the surface, posed in the key's pose, from the level it
     * lies on in the plain field posed there onto that level in the keyed one, taken back to rest through each vertex's skinning (pose
     * space deformation, Lewis, Cordner and Fong, 2000: a correction made in a pose is stored at rest, so skinning it gives it back
     * there). A key the last build made, none of whose reads has changed, is kept, carried onto the new surface's vertices.
     */
    suspend fun build(character: Character, held: HeldSkin, runAll: RunAll = IN_PARALLEL): HeldKeys = lock.withLock {
        val surface = held.surface ?: return@withLock HeldKeys.NONE
        val skin = held.skin ?: return@withLock HeldKeys.NONE
        val fields = KeyedFields(character, templates, surfaces, solids)
        val changing = character.poseKeys.map { key -> key.changes.filter { !it.isRest } }
        if (changing.all { it.isEmpty() }) return@withLock HeldKeys(surface, character, character.poseKeys, changing.map { KeyShape.EMPTY })
        fields.prepare(changing.filter { it.isNotEmpty() }, runAll)
        val base = fields.base
        val prior = last
        val kept = prior?.surface?.let { keptVertices(it, surface) }
        var made = 0
        val shapes = ArrayList<KeyShape>()
        val reads = ArrayList<KeyReads?>()
        for ((key, changes) in character.poseKeys.zip(changing)) {
            if (changes.isEmpty() || base == null) {
                shapes += KeyShape.EMPTY
                reads += null
                continue
            }
            val again = if (prior != null && kept != null) prior.keptFor(key, base, held, kept) else null
            if (again != null) {
                shapes += again.first
                reads += again.second
                continue
            }
            val keyed = fields.of(changes)
            if (keyed == null) {
                shapes += KeyShape.EMPTY
                reads += null
                continue
            }
            made++
            val (shape, read) = posedKeyShape(key, held, surface, skin, base, keyed, changes, runAll)
            shapes += shape
            reads += read
        }
        surfaces.retainUsed()
        built = made
        HeldKeys(surface, character, character.poseKeys, shapes, reads, held).also { last = it }
    }

    private fun fitOf(held: HeldSkin, surface: CharacterSurface, skin: SurfaceSkin): ImplicitSkin =
        fit?.takeIf { it.first === held }?.second ?: ImplicitSkin(held.rig, SkinSurface.of(surface, held.rig), skin).also { fit = held to it }

    private suspend fun posedKeyShape(
        key: PoseKey,
        held: HeldSkin,
        surface: CharacterSurface,
        skin: SurfaceSkin,
        base: CharacterField,
        keyed: CharacterField,
        changes: List<PartChange>,
        runAll: RunAll,
    ): Pair<KeyShape, KeyReads?> {
        val rig = held.rig
        val changed = changes.mapTo(HashSet()) { it.part }
        val bendOf = changes.filter { it.bend > 0f }.associate { it.part to it.bend }
        val bends = FloatArray(keyed.field.members.size) { m -> bendOf[keyed.runs[m].partId] ?: 0f }
        val near = changedMembers(base, changed) + changedMembers(keyed, changed)
        if (near.isEmpty() || base.field.members.size != keyed.field.members.size) return KeyShape.EMPTY to null
        val frames = boneFrames(rig, Pose(key.pose))
        val rest = surface.render.vertices
        val width = RenderMesh.FLOATS_PER_VERTEX
        val count = minOf(surface.render.vertexCount, skin.vertexCount)
        val region = (0 until count).filter { v ->
            val at = v * width
            val restPoint = Vec3(rest[at], rest[at + 1], rest[at + 2])
            near.any { (member, reach) -> member.boxDistance(restPoint) < reach }
        }.toIntArray()
        if (region.isEmpty()) return KeyShape.EMPTY to null
        val posed = posedPositions(surface, skin, rig, frames, fitOf(held, surface, skin), runAll)
        val h = base.cell * GRADIENT_SHARE
        val longest = base.cell * LONGEST_STEP_CELLS
        val plainField = PosedField(SkinSurface.of(surface, rig, base), skin, rig)
        val keyedField = PosedField(SkinSurface.of(surface, rig, keyed), skin, rig)
        val plainPosed = plainField.posed(frames)
        val keyedPosed = keyedField.posed(frames, bends)
        val boxes = plainField.posedBoxes(frames).also { plainBoxes ->
            val keyedBoxes = keyedField.posedBoxes(frames)
            for (i in plainBoxes.indices) plainBoxes[i] = if (i % 6 < 3) minOf(plainBoxes[i], keyedBoxes[i]) else maxOf(plainBoxes[i], keyedBoxes[i])
        }
        val sets = ReadSets(region, rest, posed, boxes, base, keyed)
        val offsets = FloatArray(region.size * 3)
        val turns = FloatArray(region.size * 3)
        val moves = BooleanArray(region.size)
        val differing = base.field.members.indices.filter { bends[it] > 0f || MemberSign.of(base.field.members[it]) != MemberSign.of(keyed.field.members[it]) }.toIntArray()
        runAll(chunked(region.size, KEY_CHUNK) { from, until ->
            val plain = PosedRead(plainField, plainPosed, h)
            val corrected = PosedRead(keyedField, keyedPosed, h)
            val reads = LongArray(1)
            for (i in from until until) {
                val v = region[i]
                val start = posed.vec(v * 3)
                if (differing.all { m -> unchangedAt(m, start, plainPosed, keyedPosed, base, keyed, reads) }) continue
                plain.over(sets, i)
                corrected.over(sets, i)
                val level = plain.value(start)
                if (abs(corrected.value(start) - level) < SAME_LEVEL) continue
                var p = start
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
                val shift = p - start
                if (shift.length < SAME_LEVEL) continue
                val back = blendedLinear(skin, v, frames)?.let(::inverseOf) ?: continue
                val offset = back * shift
                val turn = (back * corrected.gradient(p)).unitOr(Vec3.ZERO) - (back * plain.gradient(start)).unitOr(Vec3.ZERO)
                moves[i] = true
                offsets.put(i * 3, offset)
                turns.put(i * 3, turn)
            }
        })
        val chosen = region.indices.filter { moves[it] }
        val shape = KeyShape(
            IntArray(chosen.size) { region[chosen[it]] },
            FloatArray(chosen.size * 3) { offsets[chosen[it / 3] * 3 + it % 3] },
            FloatArray(chosen.size * 3) { turns[chosen[it / 3] * 3 + it % 3] },
        )
        return shape to KeyReads.of(sets.read, base, rig, frames, skin, shape)
    }
}

/** Every render vertex of [surface] posed by [frames] as the app poses it, linear blend refitted by implicit skinning, three floats each. */
private suspend fun posedPositions(surface: CharacterSurface, skin: SurfaceSkin, rig: Rig, frames: BoneFrames, fit: ImplicitSkin, runAll: RunAll): FloatArray {
    val posed = posedSurface(surface, skin, null, frames, runAll, fit = fit)
    val vertices = posed.surface.render.vertices
    val width = RenderMesh.FLOATS_PER_VERTEX
    return FloatArray(posed.surface.render.vertexCount * 3) { vertices[(it / 3) * width + it % 3] }
}

/**
 * Whether member [m], which the keyed field changes, reads the same at posed point [p] in both fields, and a change of its blend lies
 * too far from [p] to reach it, so the vertex there takes nothing from the key.
 */
private fun unchangedAt(m: Int, p: Vec3, plain: PosedField.Posed, keyed: PosedField.Posed, base: CharacterField, changed: CharacterField, reads: LongArray): Boolean {
    val own = plain.own(m, p, reads)
    if (abs(own - keyed.own(m, p, reads)) >= SAME_LEVEL) return false
    val a = base.field.members[m]
    val b = changed.field.members[m]
    return a.blend == b.blend || own > maxOf(a.blend, b.blend) + base.cell
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
private fun changedMembers(field: CharacterField, changed: Set<PartId>) =
    field.runs.indices.filter { field.runs[it].partId in changed }.map { i ->
        val member = field.field.members[i]
        member to member.blend + CHANGED_REACH
    }
