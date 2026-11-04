package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** About three pixels at the Sheet's scale for a part one unit long, as the silhouette tests allow. */
private const val TOLERANCE = 0.012f
private const val SHAPE_TOLERANCE = 1e-3f
private const val STATIONS = 40
private const val SECTION_POINTS = 48

/** The reach both Sides and every Top share, so a silhouette can be compared at every station. */
private const val REACH = 0.9f

/** The paw's tube closes at a fraction of its length, so the waist before its toes slides with a Side of another length. */
private const val PART_WAISTED_AT_A_FRACTION = "paw"

/** A Side that runs past every Top. */
private const val LONG_REACH = 1.3f

/** How far in from the ends a station stays: the last rings close, whatever else is drawn. */
private const val END_MARGIN = 0.1f

private val TEMPLATES = listOf("leg", "tail", "ear", "torso", "paw", "head")

/**
 * A view already drawn stays where it was when another view is drawn or redrawn: Side and Top rule what they rule at each
 * station along the part, whichever rings land there, and Front keeps its shape while Side and Top rule its size. See HLD:
 * data-model, Views set slices.
 */
class DrawnViewsHoldTest {
    private fun lens(reach: Float, half: Float, drop: Float = 0f, bulge: Float = 0f): OutlineCurve {
        fun at(i: Int) = i * reach / 8f
        fun height(a: Float) = half * (1f + bulge * sin(a * 7f)) * (0.35f + 0.65f * sin((a / reach).coerceIn(0f, 1f) * 3f + 0.1f).coerceAtLeast(0f))
        val upper = List(9) { at(it) to drop * at(it) + height(at(it)) }
        val lower = List(9) { at(8 - it) to drop * at(8 - it) - height(at(8 - it)) }
        return outline(*(upper + lower).toTypedArray())
    }

    private val slimSide = lens(REACH, 0.12f)
    private val fatSide = lens(REACH, 0.34f, drop = -0.3f, bulge = 0.2f)
    private val slimTop = lens(REACH, 0.1f, bulge = 0.1f)
    private val wideTop = lens(REACH, 0.3f)

    private fun section(x: (Float) -> Float, y: (Float) -> Float): OutlineCurve =
        outline(*List(SECTION_POINTS + 1) { val t = it * 6.2831855f / SECTION_POINTS; x(t) to y(t) }.toTypedArray())

    /** Round, and a tall egg leaning to one side. */
    private val roundFront = section({ 0.2f * sin(it) }, { 0.2f * cos(it) })
    private val lopsidedFront = section({ 0.05f + 0.3f * sin(it) + 0.08f * sin(2f * it) }, { 0.1f + 0.5f * cos(it) + 0.1f * cos(2f * it) })

    private fun build(template: PartTemplate, vararg outlines: Pair<View, OutlineCurve>) = PartBuilder.build(template, outlines = mapOf(*outlines))

    /** The lowest and highest of the surface along [direction] in the plane a = [a]: what a view of that direction shows there. */
    private fun List<Patch>.silhouetteAt(a: Float, axes: PartAxes, direction: Vec3): ClosedFloatingPointRange<Float> {
        val hits = mutableListOf<Float>()
        fun edge(from: Vec3, to: Vec3) {
            val p = from dot axes.along
            val q = to dot axes.along
            if ((p - a) * (q - a) > 0f) return
            val t = if (p == q) 0f else (a - p) / (q - p)
            hits += (from + (to - from) * t) dot direction
        }
        for (patch in this) {
            for (ring in 0 until patch.nu) {
                for (segment in 0 until patch.nv) {
                    val here = patch.point(patch.index(ring, segment))
                    edge(here, patch.point(patch.index(ring, (segment + 1) % patch.nv)))
                    if (ring + 1 < patch.nu) edge(here, patch.point(patch.index(ring + 1, segment)))
                }
            }
        }
        return hits.min()..hits.max()
    }

    private fun stations(open: Boolean): List<Float> {
        val from = if (open) END_MARGIN else 2f * END_MARGIN
        val to = REACH - 2f * END_MARGIN
        return List(STATIONS) { from + (to - from) * it / (STATIONS - 1) }
    }

    private fun assertHolds(what: String, template: PartTemplate, before: List<Patch>, after: List<Patch>, direction: (PartAxes) -> Vec3) {
        val axes = PartBuilder.axes(template)
        for (a in stations(template.openEnd != null)) {
            val was = before.silhouetteAt(a, axes, direction(axes))
            val now = after.silhouetteAt(a, axes, direction(axes))
            assertTrue(
                abs(was.start - now.start) <= TOLERANCE && abs(was.endInclusive - now.endInclusive) <= TOLERANCE,
                "${template.id.value}: $what at a = $a went from $was to $now",
            )
        }
    }

    @Test
    fun aDrawnTopStaysWhenSideIsRedrawn() {
        for (id in TEMPLATES) {
            val t = template(id)
            assertHolds(
                "Top under a new Side", t,
                build(t, View.TOP to wideTop, View.SIDE to slimSide),
                build(t, View.TOP to wideTop, View.SIDE to fatSide),
            ) { it.side }
        }
    }

    @Test
    fun aDrawnTopStaysWhenSideIsFirstDrawn() {
        for (id in TEMPLATES) {
            val t = template(id)
            assertHolds(
                "Top under a first Side", t,
                build(t, View.TOP to wideTop),
                build(t, View.TOP to wideTop, View.SIDE to fatSide),
            ) { it.side }
        }
    }

    @Test
    fun aDrawnTopStaysWhenFrontIsRedrawn() {
        for (id in TEMPLATES) {
            val t = template(id)
            assertHolds(
                "Top under a new Front", t,
                build(t, View.TOP to slimTop, View.SIDE to slimSide, View.FRONT to roundFront),
                build(t, View.TOP to slimTop, View.SIDE to slimSide, View.FRONT to lopsidedFront),
            ) { it.side }
        }
    }

    @Test
    fun aDrawnSideStaysWhenTopIsRedrawn() {
        for (id in TEMPLATES) {
            val t = template(id)
            assertHolds(
                "Side under a new Top", t,
                build(t, View.SIDE to fatSide, View.TOP to slimTop),
                build(t, View.SIDE to fatSide, View.TOP to wideTop),
            ) { it.up }
        }
    }

    @Test
    fun aDrawnSideStaysWhenFrontIsRedrawn() {
        for (id in TEMPLATES) {
            val t = template(id)
            assertHolds(
                "Side under a new Front", t,
                build(t, View.SIDE to fatSide, View.TOP to wideTop, View.FRONT to roundFront),
                build(t, View.SIDE to fatSide, View.TOP to wideTop, View.FRONT to lopsidedFront),
            ) { it.up }
        }
    }

    @Test
    fun aDrawnTopStaysWhenSideChangesLength() {
        for (id in TEMPLATES - PART_WAISTED_AT_A_FRACTION) {
            val t = template(id)
            assertHolds(
                "Top under a longer Side", t,
                build(t, View.TOP to wideTop, View.SIDE to lens(REACH, 0.2f)),
                build(t, View.TOP to wideTop, View.SIDE to lens(LONG_REACH, 0.2f)),
            ) { it.side }
        }
    }

    @Test
    fun theComparisonSeesAViewThatMoves() {
        val t = template("leg")
        assertFailsWith<AssertionError> {
            assertHolds("Top redrawn", t, build(t, View.TOP to wideTop, View.SIDE to slimSide), build(t, View.TOP to slimTop, View.SIDE to slimSide)) { it.side }
        }
    }

    /** A ring as Front draws it, with the ring's own extents taken out. */
    private fun Patch.shapeOf(ring: Int, axes: PartAxes): List<Pair<Float, Float>> {
        val points = ring(ring)
        val across = points.map { it dot axes.side }
        val rise = points.map { it dot axes.up }
        val width = across.max() - across.min()
        val height = rise.max() - rise.min()
        return points.indices.map { (across[it] - across.min()) / width to (rise[it] - rise.min()) / height }
    }

    private fun assertFrontKeepsItsShape(what: String, template: PartTemplate, before: List<Patch>, after: List<Patch>) {
        val axes = PartBuilder.axes(template)
        val was = before.first()
        val now = after.first()
        assertEquals(was.nu, now.nu)
        val ring = if (template.openEnd == null) was.nu / 2 else was.nu - 2
        val a = was.shapeOf(ring, axes)
        val b = now.shapeOf(ring, axes)
        for (i in a.indices) {
            assertEquals(a[i].first, b[i].first, SHAPE_TOLERANCE, "${template.id.value}: $what across at $i")
            assertEquals(a[i].second, b[i].second, SHAPE_TOLERANCE, "${template.id.value}: $what rise at $i")
        }
    }

    @Test
    fun aDrawnFrontKeepsItsShapeWhenSideAndTopAreRedrawn() {
        for (id in listOf("torso", "ear", "leg")) {
            val t = template(id)
            val base = build(t, View.FRONT to lopsidedFront, View.SIDE to slimSide, View.TOP to slimTop)
            assertFrontKeepsItsShape("Front under a new Side", t, base, build(t, View.FRONT to lopsidedFront, View.SIDE to fatSide, View.TOP to slimTop))
            assertFrontKeepsItsShape("Front under a new Top", t, base, build(t, View.FRONT to lopsidedFront, View.SIDE to slimSide, View.TOP to wideTop))
        }
    }

    @Test
    fun sideAndTopRuleTheSizeOfAFront() {
        val torso = template("torso")
        val axes = PartBuilder.axes(torso)
        fun spanUp(side: OutlineCurve) = build(torso, View.FRONT to roundFront, View.SIDE to side).first().ring(10).let { ring -> ring.maxOf { it dot axes.up } - ring.minOf { it dot axes.up } }
        assertTrue(spanUp(fatSide) > 1.5f * spanUp(slimSide), "Front's stored size does not set the ring's")
    }
}
