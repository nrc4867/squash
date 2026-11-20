package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable

/** How far toward the camera see-through cloth and what is drawn on it sit, in body units, so skin a pose pushes past the cloth stays under it. */
const val SEE_THROUGH_CLOTH_LIFT = 0.02f

/** Floats a vertex of see-through cloth carries in [ClothDraw.attributes]. */
const val CLOTH_FLOATS = 18

/**
 * See-through cloth over a [MeshDraw]'s mesh: [attributes] holds [CLOTH_FLOATS] floats a vertex, in order its lit and shadow tones,
 * then its opacity where it faces the viewer, how much of it is a net, the net's thread width as a share of its spacing and the index of
 * the body's run it lies on, then its place on the net, along the run in spacings and round it as the cosine and sine of 2 pi v, and
 * how far the net's place there is trusted, 0 to 1, the net's mean tone showing where it is not, then for each of the net's two sets of threads its count round the run and the sine of its slant. See [CLOTH_WGSL].
 * The cloth sits [lift] toward the camera, and so do the color layers drawn over it.
 */
@Immutable
class ClothDraw(val attributes: FloatArray, val lift: Float = SEE_THROUGH_CLOTH_LIFT)
