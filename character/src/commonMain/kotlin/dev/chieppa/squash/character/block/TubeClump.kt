package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.geom.ClumpShape
import dev.chieppa.squash.geom.VolumeShape
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** The most lobes a clump's tip ends in. */
private const val MAX_POINTS = 5

/** The most strand lines inked along a clump. */
private const val MAX_STRANDS = 4

/**
 * A clump of hair's shape in place of a tube's radius and taper when nothing is drawn: a flat, rounded lock that swells, tapers and
 * closes to a pointed, round or lobed tip, turning and waving as it goes, its root flared into what it sits on. See [ClumpShape] and
 * HLD: parts, Hair.
 */
@Serializable
@Doc("A clump of hair's shape when nothing is drawn: width, thickness, taper, tip, twist and wave, a flared root and strand lines.")
data class TubeClump(
    @Doc("Full width at the root, a fraction of the length.")
    val width: Param,
    @Doc("Full thickness at the root, a fraction of the length.")
    val thickness: Param,
    @Doc("Width toward the tip, a share of the root's.")
    val taper: Param = Param.of(0.35f),
    @Doc("How much it swells toward its first third, a share of its width.")
    val swell: Param = Param.of(0.15f),
    @Doc("The tip's roundness: 0 pointed, 1 round.")
    val tipRound: Param = Param.of(0f),
    @Doc("How far the tip closes back along it, a share of its length.")
    val tipReach: Param = Param.of(0.3f),
    @Doc("How many lobes the tip ends in, 1 to 5; above 1 the tip keeps its width.")
    val points: Param = Param.of(1f),
    @Doc("How far back between two lobes the tip is cut, a share of its length.")
    val notch: Param = Param.of(0f),
    @Doc("Degrees it turns about itself from root to tip.")
    val twist: Param = Param.of(0f),
    @Doc("How far it waves sideways, a share of its width, growing toward the tip.")
    val wave: Param = Param.of(0f),
    @Doc("How many waves along it.")
    val waves: Param = Param.of(1f),
    @Doc("How much wider its root flares into what it sits on, a share of its width.")
    val flare: Param = Param.of(0.3f),
    @Doc("How far its sides curl toward its top, a share of its half width.")
    val cup: Param = Param.of(0f),
    @Doc("How many strand lines are inked along it, on each broad face, 0 to 4.")
    val strands: Param = Param.of(0f),
    @Doc("The share of its length from the root over which its outline and strands fade in, so its root reads soft.")
    val rootSoft: Param = Param.of(0.25f),
    @Doc("The share of its length from the tip that its tip area covers.")
    val tipShade: Param = Param.of(0.3f),
) {
    /** The clump's shape at [ctx]'s values, in body units. */
    fun shape(ctx: BuildContext): ClumpShape = ClumpShape(
        width = ctx.length(width),
        thickness = ctx.length(thickness),
        taper = ctx.value(taper),
        swell = ctx.value(swell),
        tipRound = ctx.value(tipRound).coerceIn(0f, 1f),
        tipReach = ctx.value(tipReach).coerceIn(0.05f, 1f),
        points = ctx.value(points).roundToInt().coerceIn(1, MAX_POINTS),
        notch = ctx.value(notch).coerceIn(0f, 1f),
        twist = ctx.value(twist),
        wave = ctx.value(wave),
        waves = ctx.value(waves),
        flare = ctx.value(flare).coerceAtLeast(0f),
    )

    /** How its strand lines and root ink read at [values], the part's parameters over its template's defaults. */
    fun ink(values: Map<String, Float>): ClumpInk = ClumpInk(
        strands = strands.resolve(values).roundToInt().coerceIn(0, MAX_STRANDS),
        rootSoft = rootSoft.resolve(values).coerceIn(0f, 1f),
    )

    /** Where its tip area starts along it at [values]. */
    fun tipStart(values: Map<String, Float>): Float = (1f - tipShade.resolve(values)).coerceIn(0f, 1f)

    fun lint(ctx: LintContext): List<Finding> =
        listOf(
            "width" to width, "thickness" to thickness, "taper" to taper, "swell" to swell, "tipRound" to tipRound, "tipReach" to tipReach,
            "points" to points, "notch" to notch, "twist" to twist, "wave" to wave, "waves" to waves, "flare" to flare, "cup" to cup,
            "strands" to strands, "rootSoft" to rootSoft, "tipShade" to tipShade,
        ).flatMap { (field, param) -> ctx.param("clump.$field", param) }
}

/**
 * How a clump inks: [strands] lines along each broad face, and its outline and strands fading in over [rootSoft] of it from the root;
 * for a hair volume, its [flow] in their place.
 */
data class ClumpInk(val strands: Int, val rootSoft: Float, val flow: FlowInk? = null) {
    companion object {
        /** The parameters a clump's ink reads, which placing carries so its lines can read them. */
        val PARAMS: Set<String> = setOf("strands", "rootSoft")

        /** The parameters a hair volume's ink reads as well, which placing carries for a hair part. */
        val FLOW_PARAMS: Set<String> = setOf(
            "crown", "width", "back", "sides", "fringe", "rise", "flip", "fullness", "parting", "partDepth", "locks", "lockDepth", "lockJag",
            "lockSkew", "lockRound", "lockSeed", "lockFlare", "sweep", "layers", "layerGap", "lockLines",
        )
    }
}

/**
 * How a hair volume inks: [strands] lines flowing down its [shape] and gathering into its locks, the ragged edges of [layers] layers of
 * locks [layerGap] of its length apart over its hem, and lines [lockLines] of its length up from each notch between two locks. See
 * [dev.chieppa.squash.geom.HairFlow].
 */
data class FlowInk(val shape: VolumeShape, val strands: Int, val layers: Int, val layerGap: Float, val lockLines: Float)
