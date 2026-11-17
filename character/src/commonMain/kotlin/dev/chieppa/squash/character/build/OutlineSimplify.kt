package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.simplifiedPath

/** [curve] refitted to as few points as stay within [tolerance] of it, its corners and ends kept, on the kept side of [ends]' plane. */
fun simplifiedOutline(curve: OutlineCurve, tolerance: Float, ends: StrokeEnds): OutlineCurve =
    simplifiedPath(curve.toPath(), tolerance, ends as? StrokeEnds.CutAtPlane).toOutline().let { if (it.points.size < curve.points.size) it else curve }
