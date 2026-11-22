package dev.chieppa.squash.rig

import kotlin.concurrent.Volatile
import kotlin.math.abs

/** How far apart two bones' maps may be, entry by entry, and still move what they carry as one. */
private const val SAME_MAP = 1e-6f

/**
 * Whether posed surfaces are refitted to the posed field ([ImplicitSkin]); on unless a tool turns it off to time linear blend alone, as
 * the drag tool's `skinning=bones` does.
 */
object ImplicitSkinning {
    @Volatile
    var on: Boolean = true
}

/**
 * The implicit skins of one [HeldSkin]: the body's, at 0, and each garment's after it, each made the first time a pose bends what it
 * skins, and none for a surface a pose moves as one piece or not at all.
 */
internal class ImplicitSkins(val held: HeldSkin) {
    private val made = arrayOfNulls<ImplicitSkin>(1 + held.garments.size)
    private val bones = arrayOfNulls<IntArray>(made.size)

    /** Surface [index]'s implicit skin, or null where [frames] moves all its bones as one. */
    fun of(index: Int, frames: BoneFrames): ImplicitSkin? {
        val skin = (if (index == 0) held.skin else held.garments[index - 1]) ?: return null
        val surface = (if (index == 0) held.surface else held.dress?.garments?.get(index - 1)?.surface) ?: return null
        val used = bones[index] ?: skin.bones.filter { it >= 0 }.distinct().toIntArray().also { bones[index] = it }
        val maps = frames.transforms
        if (used.all { same(maps[it], maps[used[0]]) }) return null
        return made[index] ?: ImplicitSkin(held.rig, SkinSurface.of(surface, held.rig), skin).also { made[index] = it }
    }

    private fun same(a: Affine, b: Affine): Boolean =
        listOf(a.linear.x - b.linear.x, a.linear.y - b.linear.y, a.linear.z - b.linear.z, a.offset - b.offset).all { abs(it.x) <= SAME_MAP && abs(it.y) <= SAME_MAP && abs(it.z) <= SAME_MAP }
}
