package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.document.PaintedEye

/** One side of one look of a painted eye, its lids left open: what its layers are laid at, before its lids are stood. */
internal data class EyeSideKey(val eye: PaintedEye, val look: EyeLook, val side: Int) {
    companion object {
        /** The key of [eye]'s [side] in [look], whatever pose its lids stand in. */
        fun of(eye: PaintedEye, look: EyeLook, side: Int): EyeSideKey = EyeSideKey(eye.withLids(LidPose.OPEN), look, side)
    }
}

/**
 * What each side of each painted eye was last laid at on a surface, its lids open, kept between paintings so that a painting of the same
 * surface whose eyes differ only in where their lids stand lays no layer again: it stands the lids in the layers it keeps.
 */
class EyeMemo {
    private var surface: CharacterSurface? = null
    private var laid: Map<EyeSideKey, SurfaceEyes.Laid> = emptyMap()

    /** What the last painting laid on [on], nothing where it painted another surface. */
    internal fun earlier(on: CharacterSurface): Map<EyeSideKey, SurfaceEyes.Laid> = if (on === surface) laid else emptyMap()

    /** Keeps [sides], as laid on [on]. */
    internal fun keep(on: CharacterSurface, sides: Map<EyeSideKey, SurfaceEyes.Laid>) {
        surface = on
        laid = sides
    }
}
