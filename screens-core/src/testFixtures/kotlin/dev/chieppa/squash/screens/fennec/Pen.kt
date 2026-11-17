package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.recipe.pen.PenCurves
import dev.chieppa.squash.recipe.pen.PenPoint

fun p(x: Float, y: Float) = PenPoint(x, y)

fun tuft(x: Float, y: Float) = PenPoint(x, y, corner = true)

/** A smooth outline through [points], with handles from its neighbours, as a fitted hand stroke has. */
fun drawn(vararg points: PenPoint): OutlineCurve = PenCurves.drawn(points.toList())

/** A smooth outline round [points] and back to the first. */
fun loop(vararg points: PenPoint): OutlineCurve = PenCurves.loop(points.toList())
