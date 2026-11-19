package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.geom.CurvePoint

/** An ink line of a crease or a wrinkle: its [points] on the posed surface and the line's [weights] there, a share of the crease ink's. */
class CreaseLine(val points: List<CurvePoint>, val weights: FloatArray)

/**
 * One surface's creases and wrinkles in a pose: [shade], one value a vertex of the surface's drawn mesh, middles too, whose shade band
 * shows where it is at least a half, and the ink [lines]. See HLD: look, Creases and wrinkles.
 */
class CreaseMarks(val shade: FloatArray, val lines: List<CreaseLine>)

/** The creases and wrinkles of a posed surface: the body's, [body], and each worn garment's by its id, [garments]. */
class PosedCreases(val body: CreaseMarks?, val garments: Map<GarmentId, CreaseMarks> = emptyMap())
