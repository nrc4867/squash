package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.geom.CurvePoint

/** An ink line of a crease or a wrinkle: its [points] on the posed surface and the line's [weights] there, a share of the crease ink's. */
class CreaseLine(val points: List<CurvePoint>, val weights: FloatArray)

/**
 * One surface's creases and wrinkles in a pose, each one value a vertex of the surface's drawn mesh, middles too: [shade], the joint
 * creases' band, which shows where it is at least a half; [folds], the cloth's folds' lighter band, shown the same way; [lights], how
 * strongly the ridge beside each fold catches the light, 0 to 1, which dark cloth shows as a soft highlight; and the ink [lines]. See HLD:
 * look, Creases and wrinkles.
 */
class CreaseMarks(val shade: FloatArray, val lines: List<CreaseLine>, val folds: FloatArray? = null, val lights: FloatArray? = null)

/** The creases and wrinkles of a posed surface: the body's, [body], and each worn garment's by its id, [garments]. */
class PosedCreases(val body: CreaseMarks?, val garments: Map<GarmentId, CreaseMarks> = emptyMap())
