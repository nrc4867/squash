package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.ShineBand
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.TubeSpec
import dev.chieppa.squash.geom.VolumeShape
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** The area a volume's hem colour fills. */
const val HEM_AREA = "hem"

/** The area a volume's parting colour fills. */
const val PARTING_AREA = "parting"

/** The area a volume's band of shine fills, on it and on the hair laid over it. */
const val SHINE_AREA = "shine"

/** The share of the volume's length, from its front, its parting area runs back over. */
private const val PARTING_AREA_REACH = 0.55f

/**
 * A hair volume: one closed run from the back of the head to the front, in the field over the skull, shaped by its parameters when
 * nothing is drawn (see [VolumeShape]) and by its outlines when drawn, as a skull is. It paints a hem along its underside, a parting
 * along its top and a band of shine round its crown. See HLD: parts, Hair.
 */
@Serializable
@SerialName("volume")
@Doc("A hair volume over the skull: one closed run, shaped by its parameters when nothing is drawn, with hem, parting and shine areas.")
data class Volume(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span = Span(0f, 1f),
    @Doc("How far its top rises above its axis, a fraction of the length.")
    val crown: Param,
    @Doc("Its half width, a fraction of the length.")
    val width: Param,
    @Doc("How far it hangs below its axis at the back, a fraction of the length.")
    val back: Param,
    @Doc("How far it hangs below its axis at the sides, a fraction of the length.")
    val sides: Param,
    @Doc("How far it hangs below its axis at the front, a fraction of the length.")
    val fringe: Param,
    @Doc("How far its rings' middles rise toward the front, a fraction of the length, so its front closes on the forehead.")
    val rise: Param = Param.of(0f),
    @Doc("How far its hem flares out, a share of its half width.")
    val flip: Param = Param.of(0f),
    @Doc("How full it stays toward its front and back: 2 rounds it as an ellipse, higher squares it.")
    val fullness: Param = Param.of(2.5f),
    @Doc("Where round its top its parting lies: 0 the middle, toward 0.25 the side v 0.25 faces, negative the other side.")
    val parting: Param = Param.of(0f),
    @Doc("How deep the parting dents its top, a share of its crown.")
    val partDepth: Param = Param.of(0f),
    @Doc("How far up from each ring's lowest point its hem area reaches, a fraction of the length.")
    val hemShade: Param = Param.of(0.2f),
    @Doc("How wide its parting area is, a share of a turn.")
    val partShade: Param = Param.of(0.012f),
    @Doc("The height of its band of shine on the sphere through its rings, -1 underneath to 1 at the crown.")
    val shineAt: Param = Param.of(0.55f),
    @Doc("How wide its band of shine is, on the same sphere.")
    val shineWidth: Param = Param.of(0.12f),
    @Doc("How deep the band's lower edge is cut into points.")
    val shineJag: Param = Param.of(0f),
    @Doc("How many points the band's lower edge is cut into round the crown.")
    val shineTeeth: Param = Param.of(0f),
    @Doc("How many locks its hem parts into round the head, seen from above; 0 for a whole hem.")
    val locks: Param = Param.of(0f),
    @Doc("How far up between two locks the hem is cut, a fraction of the length.")
    val lockDepth: Param = Param.of(0f),
    @Doc("How much shorter some locks are than others, a share of the cut: a ragged hem.")
    val lockJag: Param = Param.of(0f),
    @Doc("How far each lock's point leans toward one neighbour, -1 to 1.")
    val lockSkew: Param = Param.of(0f),
    @Doc("How round the locks' points are: 0 pointed, 1 round.")
    val lockRound: Param = Param.of(0f),
    @Doc("Which locks are the shorter ones.")
    val lockSeed: Param = Param.of(0f),
    @Doc("How far each lock's point splays out sideways, a share of the half width.")
    val lockFlare: Param = Param.of(0f),
    @Doc("How far the fringe sweeps aside: longer toward the side v 0.25 faces when positive, toward the other when negative.")
    val sweep: Param = Param.of(0f),
    @Doc("How many strand lines are inked flowing down over it from its crown.")
    val strands: Param = Param.of(0f),
) : Block {
    override fun run() = u

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    /** The undrawn shape at [values], its axis [length] long. */
    fun shape(values: Map<String, Float>, length: Float) = VolumeShape(
        length = length,
        crown = crown.resolve(values),
        width = width.resolve(values),
        back = back.resolve(values),
        sides = sides.resolve(values),
        fringe = fringe.resolve(values),
        flip = flip.resolve(values),
        fullness = fullness.resolve(values).coerceIn(1.2f, 8f),
        parting = parting.resolve(values),
        partDepth = partDepth.resolve(values).coerceAtLeast(0f),
        rise = rise.resolve(values),
        locks = locks.resolve(values).roundToInt().coerceAtLeast(0),
        lockDepth = lockDepth.resolve(values).coerceAtLeast(0f),
        lockJag = lockJag.resolve(values).coerceIn(0f, 1f),
        lockSkew = lockSkew.resolve(values),
        lockRound = lockRound.resolve(values).coerceIn(0f, 1f),
        lockSeed = lockSeed.resolve(values).roundToInt(),
        lockFlare = lockFlare.resolve(values),
        sweep = sweep.resolve(values),
    )

    override fun clumpInk(values: Map<String, Float>): ClumpInk? =
        strands.resolve(values).roundToInt().takeIf { it > 0 }?.let { ClumpInk(it, 0f, flowing = true) }

    private fun band(values: Map<String, Float>) = ShineBand(
        shineAt.resolve(values),
        shineWidth.resolve(values).coerceAtLeast(0f),
        shineJag.resolve(values).coerceAtLeast(0f),
        shineTeeth.resolve(values).roundToInt().coerceAtLeast(0),
    )

    override fun build(ctx: BuildContext): List<Patch> {
        val segments = ctx.template.topology.segments
        val views = ctx.views
        if (views != null) {
            val rings = ctx.template.topology.rings
            return listOf(
                SliceBuilder.tube(
                    TubeSpec(
                        name = name ?: BODY_PATCH,
                        axis = ctx.axis,
                        uStart = u.start,
                        uEnd = u.end,
                        rings = rings,
                        segments = segments,
                        section = ctx.template.section.spline(segments),
                        radius = ctx.axisLength / 2f,
                        roundRings = rings / 2,
                        startClosure = rounded(ctx.closure(End.START, u.start)),
                        endClosure = rounded(ctx.closure(End.END, u.end)),
                        jointUs = ctx.jointUs,
                        socketNormal = ctx.axes.along,
                        views = views,
                        front = ctx.front,
                        up = ctx.template.axis.up?.toVec3(),
                    ),
                ),
            )
        }
        val rings = ctx.template.topology.rings
        val us = List(rings) { u.start + (u.end - u.start) * it / (rings - 1) }
        val slices = shape(ctx.values, ctx.axisLength).slices(ctx.axis, us, ctx.template.axis.up?.toVec3() ?: ctx.axes.up, segments)
        return listOf(SliceBuilder.loft(name ?: BODY_PATCH, slices, segments))
    }

    private fun rounded(closure: EndClosure) = if (closure == EndClosure.FLAT) EndClosure.ROUND else closure

    override fun areas(values: Map<String, Float>): List<AreaSpec> {
        val patch = name ?: BODY_PATCH
        val hem = hemShade.resolve(values).coerceAtLeast(0.005f)
        val partV = parting.resolve(values)
        val part = partShade.resolve(values).coerceIn(0.002f, 0.2f)
        val loops = stretched(band(values).loops())
        return listOf(
            AreaSpec(HEM_AREA, Span(u.start, u.end), Span(0.25f, 0.75f), AreaTag.FILL, patch, loops = stretched(shape(values, 1f).hemLoops(hem))),
            AreaSpec(PARTING_AREA, Span(u.end - (u.end - u.start) * PARTING_AREA_REACH, u.end), Span(wrap(partV - part), wrap(partV + part)), AreaTag.FILL, patch),
            AreaSpec(SHINE_AREA, Span(u.start, u.end), Span(0f, 1f), AreaTag.FILL, patch, loops = loops),
        )
    }

    override fun hairShine(values: Map<String, Float>, length: Float): HairShine = HairShine(shape(values, length), band(values))

    /** [loops] of places on the whole run's own (u, v) laid on this block's stretch of u. */
    private fun stretched(loops: List<List<Vec2>>) = loops.map { loop -> loop.map { u.start + (u.end - u.start) * it.x to it.y } }

    private fun wrap(v: Float): Float = v - kotlin.math.floor(v)

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + listOf(
            "crown" to crown, "width" to width, "back" to back, "sides" to sides, "fringe" to fringe, "rise" to rise, "flip" to flip, "fullness" to fullness,
            "parting" to parting, "partDepth" to partDepth, "hemShade" to hemShade, "partShade" to partShade, "shineAt" to shineAt,
            "shineWidth" to shineWidth, "shineJag" to shineJag, "shineTeeth" to shineTeeth, "locks" to locks, "lockDepth" to lockDepth,
            "lockJag" to lockJag, "lockSkew" to lockSkew, "lockRound" to lockRound, "lockSeed" to lockSeed, "lockFlare" to lockFlare, "sweep" to sweep, "strands" to strands,
        ).flatMap { (field, param) -> ctx.param(field, param) }
}

/** A hair volume's undrawn [shape] and the band of [shine] round it, which hair laid over it shows as well. */
data class HairShine(val shape: VolumeShape, val shine: ShineBand)
