package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.outlineEnds
import dev.chieppa.squash.character.build.oversketchedOutline
import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.build.viewPoint
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.meshProfile
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far a rebuilt silhouette may lie from the undrawn one, as a share of the part's reach in that view: three pixels across a pane of 300. */
private const val TOLERANCE = 0.01f

/**
 * A Front outline sets only a section's shape: it is resampled to the template's control points, which round it, and scaled
 * to the unit span, so a part wider than tall, as the head's skull is, takes that proportion twice.
 */
private const val FRONT_TOLERANCE = 0.03f

/**
 * A drawn paw with toes cannot show its undrawn Side and Top. Its tube closes where its toes begin, which leaves a waist the
 * outline does not set, and drawn toes share the width side by side where undrawn ones fan out to the corners.
 */
private const val TOED_PAW_TOLERANCE = 0.045f

/**
 * A part whose undrawn axis bends, a digitigrade leg, stands its undrawn rings square to the bent axis, and a drawing's on planes
 * a = constant, so at the bend the two silhouettes differ by the rings' tilt across the part's thickness.
 */
private const val BENT_TOLERANCE = 0.03f

/** Whether [template]'s axis bends with nothing drawn. */
private fun bendsUndrawn(template: PartTemplate): Boolean = template.axis.bend?.let { it.resolve(template.defaults) != 0f } ?: false

/** With all three views started, each is held to the others as well, which the head's proportions show most. */
private const val IN_TURN_TOLERANCE = 0.035f

/** A toed part with all three views started: its toes' waist and shared width, held to the other views as well. */
private const val TOED_IN_TURN_TOLERANCE = 0.06f

/** Whether [template] builds toes with nothing drawn, which a drawn outline then shares its width among. */
private fun hasToes(template: PartTemplate): Boolean = PartBuilder.build(template).any { it.name.substringBefore(':') != it.name && !it.name.startsWith("pad:") }

/**
 * Whether [view] of [template] sees across the row a moved digit, a thumb, stands out of: drawn, the width is the palm's and the
 * shared digits', and the thumb keeps its own place outside it, so the undrawn silhouette is not one a drawing makes again.
 */
private fun thumbWidth(template: PartTemplate, view: View): Boolean =
    template.views[view] != Sets.HEIGHT && template.blocks.any { it is ToeRow && it.digits.isNotEmpty() }

/** Start from template through the real build: the outline it makes rebuilds the silhouette it was made from. */
class TemplateOutlineTest {
    private fun profile(template: PartTemplate, patches: List<Patch>, view: View, scale: Float = 1f): OutlineProfile {
        val axes = PartBuilder.axes(template)
        val mesh = PartBuilder.mesh(template.id.value, template, patches)
        return assertNotNull(meshProfile(mesh.patches) { axes.viewPoint(template.planeOf(view), it * scale) }, "${template.id.value} $view")
    }

    /** The furthest either silhouette lies from the other, as the eye measures it. */
    private fun apart(a: OutlineProfile, b: OutlineProfile): Float {
        fun oneWay(from: List<Vec2>, to: List<Vec2>) = from.maxOf { p -> (1 until to.size).minOf { distance(p, to[it - 1], to[it]) } }
        return max(oneWay(a.ring(), b.ring()), oneWay(b.ring(), a.ring()))
    }

    private fun distance(p: Vec2, a: Vec2, b: Vec2): Float {
        val along = b - a
        val square = along.x * along.x + along.y * along.y
        val t = if (square == 0f) 0f else (((p.x - a.x) * along.x + (p.y - a.y) * along.y) / square).coerceIn(0f, 1f)
        val dx = a.x + along.x * t - p.x
        val dy = a.y + along.y * t - p.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun reach(profile: OutlineProfile): Float {
        val ring = profile.ring()
        return max(ring.maxOf { it.x } - ring.minOf { it.x }, ring.maxOf { it.y } - ring.minOf { it.y })
    }

    private fun assertWatertight(template: PartTemplate, patches: List<Patch>, label: String) {
        val mesh = PartBuilder.mesh(template.id.value, template, patches)
        for (patch in mesh.patches) {
            assertEquals(emptyList(), MeshInvariants.check(patch, PartBuilder.openRings(template, patch.patch)), label)
        }
    }

    private fun outlineOf(template: PartTemplate, view: View, data: PartData = PartData()): OutlineCurve =
        assertNotNull(templateOutline(template, data, view), "${template.id.value} $view")

    private fun toleranceFor(template: PartTemplate, view: View): Float? = when {
        thumbWidth(template, view) -> null
        view == View.FRONT -> FRONT_TOLERANCE
        hasToes(template) -> TOED_PAW_TOLERANCE
        bendsUndrawn(template) -> BENT_TOLERANCE
        else -> TOLERANCE
    }

    @Test
    fun theOutlineRebuildsTheUndrawnSilhouetteOnEveryTemplateAndView() {
        val failures = buildList {
            for (template in shipped.parts) {
                val undrawn = PartBuilder.build(template)
                for (view in View.entries) {
                    val label = "${template.id.value} $view"
                    val built = PartBuilder.build(template, outlines = mapOf(view to outlineOf(template, view)))
                    assertWatertight(template, built, label)
                    val before = profile(template, undrawn, view)
                    val share = apart(before, profile(template, built, view)) / reach(before)
                    val tolerance = toleranceFor(template, view) ?: continue
                    if (share > tolerance) add("$label: $share")
                }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun aPawWithToesShowsItsWaistAndSharedWidthOnce() {
        val paw = template("paw")
        for (toes in listOf(3f, 4f, 5f)) {
            val params = mapOf("toes" to toes)
            val data = PartData(params = params)
            val undrawn = PartBuilder.build(paw, params)
            for (view in View.entries) {
                val built = PartBuilder.build(paw, params, mapOf(view to outlineOf(paw, view, data)))
                assertWatertight(paw, built, "$toes toes $view")
                val before = profile(paw, undrawn, view)
                val share = apart(before, profile(paw, built, view)) / reach(before)
                assertTrue(share <= TOED_PAW_TOLERANCE, "$toes toes $view: $share")
            }
        }
    }

    @Test
    fun anOpenViewEndsOnTheSocketPlaneAndAClosedOneMeetsItself() {
        for (template in shipped.parts) {
            for (view in View.entries) {
                val curve = outlineOf(template, view)
                if (template.openEnd != null && view != View.FRONT) {
                    assertEquals(0f, curve.points.first().x, "${template.id.value} $view")
                    assertEquals(0f, curve.points.last().x, "${template.id.value} $view")
                    assertTrue(curve.toPath().flatten().xs.all { it >= -1e-3f }, "${template.id.value} $view keeps to the part's side")
                } else {
                    assertEquals(curve.points.first(), curve.points.last(), "${template.id.value} $view")
                }
            }
        }
    }

    @Test
    fun startingEveryViewInTurnStaysWatertight() {
        for (template in shipped.parts) {
            var data = PartData()
            val read = View.entries.filter { it in template.views }
            for (view in read) {
                data = PartData(outlines = data.outlines + (view to outlineOf(template, view, data)))
                assertWatertight(template, PartBuilder.build(template, outlines = data.outlines), "${template.id.value} up to $view")
            }
            val undrawn = PartBuilder.build(template)
            val built = PartBuilder.build(template, outlines = data.outlines)
            for (view in read) {
                if (thumbWidth(template, view)) continue
                val before = profile(template, undrawn, view)
                val share = apart(before, profile(template, built, view)) / reach(before)
                assertTrue(share <= if (hasToes(template)) TOED_IN_TURN_TOLERANCE else IN_TURN_TOLERANCE, "${template.id.value} $view with all three started: $share")
            }
        }
    }

    @Test
    fun theOutlineIsOneAnOversketchCanRedraw() {
        val leg = template("leg")
        val curve = outlineOf(leg, View.SIDE)
        val pixel = 0.002f
        val stroke = List(40) { CurvePoint(0.15f + it * 0.005f, 0.09f) }
        val done = assertNotNull(oversketchedOutline(curve, stroke, outlineEnds(View.SIDE, leg.openEnd), pixel))
        assertNotEquals(curve, done.curve)
        assertWatertight(leg, PartBuilder.build(leg, outlines = mapOf(View.SIDE to done.curve)), "oversketched")
    }

    @Test
    fun itFollowsTheSizeParameterAndTheViewsAlreadyDrawn() {
        val leg = template("leg")
        val base = outlineOf(leg, View.SIDE)
        val doubled = outlineOf(leg, View.SIDE, PartData(params = mapOf("size" to 2f)))
        assertEquals(base.toPath().flatten().xs.max(), doubled.toPath().flatten().xs.max(), 1e-3f, "outlines hold the drawing before size multiplies it")
        val drawnSide = outline(0f to 0.3f, 0.4f to 0.25f, 0.8f to 0.1f, 0.8f to -0.1f, 0.4f to -0.25f, 0f to -0.3f)
        val data = PartData(outlines = mapOf(View.SIDE to drawnSide))
        val top = outlineOf(leg, View.TOP, data)
        assertEquals(0.8f, top.toPath().flatten().xs.max(), 0.02f, "Top reaches as far as the Side already drawn")
        val built = PartBuilder.build(leg, outlines = mapOf(View.SIDE to drawnSide, View.TOP to top))
        val before = profile(leg, PartBuilder.build(leg, outlines = mapOf(View.SIDE to drawnSide)), View.TOP)
        assertTrue(apart(before, profile(leg, built, View.TOP)) / reach(before) <= TOLERANCE)
    }

    @Test
    fun aPointOnAViewComesBackThroughViewPoint() {
        val axes = PartBuilder.axes(template("ear"))
        val point = Vec3(0.2f, 0.5f, -0.1f)
        assertEquals(point dot axes.along, axes.viewPoint(View.SIDE, point).x, 1e-5f)
        assertEquals(point dot axes.side, -axes.viewPoint(View.TOP, point).y, 1e-5f)
        assertEquals(point dot axes.side, axes.viewPoint(View.FRONT, point).x, 1e-5f)
    }
}
