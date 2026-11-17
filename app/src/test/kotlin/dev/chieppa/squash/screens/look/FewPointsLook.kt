package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.centreLine
import dev.chieppa.squash.character.build.outlineEnds
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CentreLine
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.geom.resample
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.SYMMETRIC_VIEWS
import dev.chieppa.squash.screens.smoke.CloseUps
import kotlin.math.hypot
import kotlin.math.sin

/** A redrawing hand lays its samples this many screen pixels apart, and wobbles across the line by this many at most. */
/** The redrawn part's longest span fills this many screen pixels. */
private const val SHOWN_PIXELS = 300f

private const val HAND_SPACING_PIXELS = 3f
private const val HAND_WOBBLE_PIXELS = 0.8f
private const val WOBBLE_RATE = 0.37f

/**
 * [start] with every outline drawn again by hand and fitted at [fit], as Draw outline fits a release: each outline is traced
 * at an even spacing with a steady wobble across it, as if its part's longest span filled [SHOWN_PIXELS] screen
 * pixels, and the trace is cleaned and fitted with the outline's own ends. With [mirrored], a part down the body's middle is
 * redrawn in Top and Front as Mirror draws it there: only the half of its outline on one side of its line of symmetry is
 * traced, and the fit takes that half and its mirror image.
 */
fun redrawnDocument(start: SquashDocument, fit: Float, mirrored: Boolean = false): SquashDocument = TestWork().use { work ->
    val session = CharacterSession(start, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
    work.settle(session)
    val character = session.document.value.character
    val seen = mutableSetOf<Map<View, OutlineCurve>>()
    for (part in character.parts) {
        val outlines = character.resolved(part).outlines
        if (outlines.isEmpty() || !seen.add(outlines)) continue
        val openEnd = session.builtTemplate(part).openEnd
        val span = outlines.values.maxOf { curve -> maxOf(curve.points.maxOf { it.x } - curve.points.minOf { it.x }, curve.points.maxOf { it.y } - curve.points.minOf { it.y }) }
        val pixel = span / SHOWN_PIXELS
        val middle = part.pair == null && part.side == null
        for ((view, curve) in outlines) {
            val half = centreLine(view).takeIf { mirrored && middle && view in SYMMETRIC_VIEWS }
            val traced = handTrace(curve, pixel).let { line -> half?.let { onOneSide(line, it) } ?: line }
            val fitted = cleanStroke(traced, outlineEnds(view, openEnd), pixel, StrokeOptions(fit = fit, half = half)) ?: continue
            session.send(SetOutline(part.id, view, fitted.path.toOutline()))
        }
    }
    work.settle(session)
    session.document.value
}

private fun handTrace(curve: OutlineCurve, pixel: Float): List<Vec2> {
    val flat = curve.toPath().flatten(64)
    val line = flat.xs.indices.map { Vec2(flat.xs[it], flat.ys[it]) }
    val even = resample(line, HAND_SPACING_PIXELS * pixel)
    return even.mapIndexed { i, p ->
        if (i == 0 || i == even.lastIndex) return@mapIndexed p
        val run = even[i + 1] - even[i - 1]
        val length = hypot(run.x, run.y).takeIf { it > 0f } ?: return@mapIndexed p
        val wobble = HAND_WOBBLE_PIXELS * pixel * sin(i * WOBBLE_RATE) / length
        Vec2(p.x - run.y * wobble, p.y + run.x * wobble)
    }
}

/** The longest run of [line], read round as a loop, on the positive side of [centre]. */
private fun onOneSide(line: List<Vec2>, centre: CentreLine): List<Vec2> {
    val ring = if (line.first() == line.last()) line.dropLast(1) else line
    val outside = ring.indexOfFirst { centre.across(it) < 0f }.takeIf { it >= 0 } ?: return line
    val turned = ring.subList(outside, ring.size) + ring.subList(0, outside)
    var best = emptyList<Vec2>()
    var run = mutableListOf<Vec2>()
    for (p in turned + turned.first()) {
        if (centre.across(p) >= 0f) run += p else {
            if (run.size > best.size) best = run
            run = mutableListOf()
        }
    }
    return if (best.size >= 2) best else line
}
