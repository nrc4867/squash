package dev.chieppa.squash.geom

/** How a stroke is cleaned up, from the drawer's settings. */
data class StrokeOptions(
    /** From 0 to 1. */
    val smoothing: Float = DEFAULT_STROKE_SMOOTH,
    /** A section between corners that lies almost on its chord becomes exactly straight. */
    val snapLine: Boolean = true,
    /** A stroke's ends are joined to each other or to the socket plane. Off, they stay where they are and the build reads the chord. */
    val closeEnd: Boolean = true,
    /** Draws one edge of a shape, mirrored across this line. */
    val half: CentreLine? = null,
    /** What the hand forced along this stroke, in the stroke's own units. */
    val marks: List<StrokeMark> = emptyList(),
)

/**
 * What releasing a stroke now would make, in the plane's units. [cleanStroke] fits [shape] and nothing else, so the two
 * cannot differ by more than the fit's tolerance.
 */
class StrokePreview(
    /** The polyline release fits; empty if the stroke makes no outline. */
    val shape: List<Vec2>,
    val closed: Boolean,
    /** The stretches of the stroke that stay. */
    val drawn: List<List<Vec2>>,
    /** What release adds to join the ends: back to the start or along x to the socket plane. */
    val connectors: List<List<Vec2>>,
    /** The stretches of the stroke release discards. */
    val dropped: List<List<Vec2>>,
    /** Where the stroke began. */
    val start: Vec2,
    /** Whether the stroke has come back within reach of [start], so it can be let go. */
    val closes: Boolean,
)

/** The fit's input and how each part of it came about: the pure first half of [cleanStroke]. */
fun strokePreview(samples: List<Vec2>, ends: StrokeEnds, pixel: Float, options: StrokeOptions = StrokeOptions()): StrokePreview {
    options.half?.let { return halfPreview(samples, ends, pixel, options, it) }
    val nothing = StrokePreview(emptyList(), false, emptyList(), emptyList(), listOf(samples), samples.first(), false)
    if (samples.size < 2 || arcLength(samples) < strokeMinLength(pixel)) return nothing
    val spacing = strokeSpacing(pixel)
    val dense = resample(samples, spacing)
    val passes = smoothPasses(options.smoothing)
    val cleaner = PreviewBuilder(samples.first(), pixel, options, passes)
    val plane = ends as? StrokeEnds.CutAtPlane
    if (!options.closeEnd) return cleaner.leftOpen(dense, plane) ?: nothing
    val loop = findLoop(dense, closeGap(pixel), spacing)
    if (plane == null) return cleaner.closedLoop(loop ?: chordLoop(dense, spacing))
    if (loop != null) return cleaner.loopOnPlane(loop, plane) ?: nothing
    val line = smoothed(dense, closed = false, passes, options.marks, pixel)
    val cut = cutAtPlane(line, closed = false, plane, spacing) ?: return nothing
    if (stubCrossesStroke(cut, closeGap(pixel))) return cleaner.loopOnPlane(chordLoop(dense, spacing), plane) ?: nothing
    return cleaner.cut(cut, emptyList(), line.size) ?: nothing
}

private class PreviewBuilder(val start: Vec2, val pixel: Float, val options: StrokeOptions, val passes: Int) {
    private val spacing = strokeSpacing(pixel)

    /** A loop in a view that keeps it closed. */
    fun closedLoop(loop: StrokeLoop): StrokePreview {
        val line = smoothed(loop.line, closed = true, passes, options.marks, pixel)
        val shape = resample(line, spacing)
        val drawn = line.subList(0, loop.closingFrom + 1)
        val closing = line.subList(loop.closingFrom, line.size)
        return StrokePreview(
            if (options.snapLine) straightened(shape, closed = true, pixel, options.marks) else shape,
            true,
            listOf(drawn),
            listOfNotNull(closing.takeIf { it.size > 1 }),
            loop.overshoot,
            start,
            loop.closes,
        )
    }

    /** A loop opened at a socket plane. */
    fun loopOnPlane(loop: StrokeLoop, plane: StrokeEnds.CutAtPlane): StrokePreview? {
        val line = smoothed(loop.line, closed = true, passes, options.marks, pixel)
        val cut = cutAtPlane(line, closed = true, plane, spacing) ?: return null
        return cut(cut, loop.overshoot, loop.closingFrom, loop.closes)
    }

    /** What [cut] keeps of its source, whose edges from [closingFrom] on were not drawn. */
    fun cut(cut: PlaneCut, overshoot: List<List<Vec2>>, closingFrom: Int, closes: Boolean = false): StrokePreview? {
        val line = cut.line
        if (arcLength(line) < strokeMinLength(pixel)) return null
        val shape = resample(line, spacing)
        val isDrawn = { edge: Int -> edge < closingFrom }
        return StrokePreview(
            if (options.snapLine) straightened(shape, closed = false, pixel, options.marks) else shape,
            false,
            pieces(cut.run.kept, isDrawn),
            listOfNotNull(cut.startStub, cut.endStub) + pieces(cut.run.kept) { !isDrawn(it) },
            overshoot + cut.run.rest.flatMap { pieces(it, isDrawn) },
            start,
            closes,
        )
    }

    /** A stroke with Close end off: its own ends, cut only where it passes a socket [plane]. */
    fun leftOpen(dense: List<Vec2>, plane: StrokeEnds.CutAtPlane?): StrokePreview? {
        val line = smoothed(dense, closed = false, passes, options.marks, pixel)
        val run = plane?.let { longestRun(line, closed = false, it.plane, if (it.above) 1f else -1f) ?: return null }
        val kept = run?.points ?: line
        if (arcLength(kept) < strokeMinLength(pixel)) return null
        val shape = resample(kept, spacing)
        return StrokePreview(
            if (options.snapLine) straightened(shape, closed = false, pixel, options.marks) else shape,
            false,
            listOf(kept),
            emptyList(),
            run?.rest?.map { it.line }.orEmpty(),
            start,
            false,
        )
    }
}

/** The stretches of [piece] over the source edges [wanted] says. */
private inline fun pieces(piece: RunPiece, wanted: (Int) -> Boolean): List<List<Vec2>> {
    val found = mutableListOf<List<Vec2>>()
    var from = -1
    for (i in piece.edges.indices) {
        if (wanted(piece.edges[i])) {
            if (from < 0) from = i
        } else if (from >= 0) {
            found += piece.line.subList(from, i + 1)
            from = -1
        }
    }
    if (from >= 0) found += piece.line.subList(from, piece.edges.size + 1)
    return found
}
