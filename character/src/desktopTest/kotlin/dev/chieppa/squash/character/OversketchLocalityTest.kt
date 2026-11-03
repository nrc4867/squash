package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.oversketchedOutline
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurveLimit
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.document.changedSections
import dev.chieppa.squash.document.moved
import dev.chieppa.squash.document.sections
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.golden.GoldenFixture
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val PIXEL = 0.005f
private const val LENGTH = 1.6f
private const val SIDE_HEIGHT = 0.3f
private const val TOP_WIDTH = 0.22f
private const val STROKE_SAMPLES = 200
private const val EDIT_FROM = 0.5f
private const val EDIT_TO = 0.9f
private const val BUMP = 0.03f
private const val DRAG = 0.04f
private const val WAVE = 0.25f
private const val WAVES = 14f

/** Two axis knots and one table station, as a share of the length, on each side of a touched span. */
private const val MARGIN = 0.04f

private val PLANE = StrokeEnds.CutAtPlane()
private val LIMIT = CurveLimit(0f, above = true)

/**
 * Exit criterion 2: an edit to a Side or Top span moves only the rings within the span and its margin, compared as raw
 * float bits through the real session, and the far-end and Front edits that reach further are pinned down as such.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OversketchLocalityTest {
    private val id = PartId("leg-1")
    private val side = drawn(SIDE_HEIGHT)
    private val top = drawn(TOP_WIDTH)

    /** A stroke from the socket plane around to the far end and back, cleaned as the Draw tool cleans one. */
    private fun drawn(height: Float): OutlineCurve {
        val samples = List(STROKE_SAMPLES) { i ->
            val t = i.toFloat() / (STROKE_SAMPLES - 1)
            val upper = t < 0.5f
            val x = LENGTH * (if (upper) t * 2f else (1f - t) * 2f)
            val y = upperRun(x, height) * (if (upper) 1f else -1f)
            Vec2(x, y)
        }
        return assertNotNull(cleanStroke(samples, PLANE, PIXEL)).path.toOutline()
    }

    /** A rounded run with a wave in it, so the fit keeps several sections as a real drawing does. */
    private fun upperRun(x: Float, height: Float): Float {
        val round = sqrt((1f - (x / LENGTH) * (x / LENGTH)).coerceAtLeast(0f))
        return height * round * (1f + WAVE * sin(WAVES * x))
    }

    /** A stroke along the upper run from [EDIT_FROM] to [EDIT_TO] with a bump in it. */
    private fun bump(height: Float): List<CurvePoint> = List(STROKE_SAMPLES) { i ->
        val t = i.toFloat() / (STROKE_SAMPLES - 1)
        val x = EDIT_FROM + (EDIT_TO - EDIT_FROM) * t
        CurvePoint(x, upperRun(x, height) + BUMP * sin(PI.toFloat() * t))
    }

    private fun TestScope.session(leg: PartInstance): CharacterSession = CharacterSession(
        SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1), listOf(leg))),
        TemplateLibrary(shipped.parts),
        backgroundScope,
        StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.drawnSession(): CharacterSession {
        val leg = PartInstance(id, TemplateRef(TemplateId("leg"), 1), own = PartData(outlines = mapOf(View.SIDE to side, View.TOP to top)))
        return session(leg).also {
            it.mesh(id)
            runCurrent()
            assertTrue(it.graph.settled.value)
        }
    }

    private fun CharacterSession.body(): Patch = assertNotNull(mesh(id).output.value).patches.single().patch

    /** The rings of [before] and [after] that differ in any raw float bit. */
    private fun changedRings(before: Patch, after: Patch): Set<Int> = (0 until before.nu).filter { ring ->
        (0 until before.nv).any { v ->
            val at = before.index(ring, v)
            val a = before.point(at)
            val b = after.point(at)
            a.x.toRawBits() != b.x.toRawBits() || a.y.toRawBits() != b.y.toRawBits() || a.z.toRawBits() != b.z.toRawBits()
        }
    }.toSet()

    /** How far sections [range] of [curve] reach along the axis. */
    private fun reach(curve: OutlineCurve, range: IntRange): ClosedFloatingPointRange<Float> {
        val flat = BezierPath(curve.toPath().segments.slice(range)).flatten()
        return flat.xs.min()..flat.xs.max()
    }

    /**
     * Rings further than the margin from what the edit touched are the same bits; some ring inside moved. [touched] is the
     * edited stretch along the axis, before and after.
     */
    private fun assertLocal(before: Patch, after: Patch, touched: List<ClosedFloatingPointRange<Float>>) {
        val from = touched.minOf { it.start }
        val to = touched.maxOf { it.endInclusive }
        val margin = MARGIN * LENGTH
        val changed = changedRings(before, after)
        assertTrue(changed.isNotEmpty(), "the touched span changed")
        val slices = assertNotNull(before.slices)
        for (ring in changed) {
            val at = slices[ring].u * LENGTH
            assertTrue(at in (from - margin)..(to + margin), "ring $ring at $at moved, outside $from..$to and its margin")
        }
        assertTrue(slices.indices.count { slices[it].u * LENGTH !in (from - margin)..(to + margin) } > 0, "some rings lie outside")
    }

    private fun fixture(name: String, patch: Patch) {
        val text = (0 until patch.nu * patch.nv).joinToString("") { i ->
            val p = patch.point(i)
            "%.4f %.4f %.4f\n".format(Locale.ROOT, p.x, p.y, p.z)
        }
        GoldenFixture.check(Path.of("src/desktopTest/golden/oversketch-$name.txt"), text, "character")
    }

    /** The first anchor on the upper run past [EDIT_FROM], with its next handle. */
    private fun OutlineCurve.handleInSpan(): Int =
        anchors.first { points[it].x > EDIT_FROM && points[it].y > 0f && it + 1 < points.lastIndex } + 1

    /** A handle drag in [view] sent as a gesture, as the tool sends it: previews, then one undo step. */
    private fun TestScope.dragHandle(view: View, curve: OutlineCurve, name: String) {
        val session = drawnSession()
        val before = session.body()
        fixture("before", before)
        val index = curve.handleInSpan()
        val start = curve.points[index]
        val gesture = session.history.begin()
        var last = curve
        repeat(4) { step ->
            last = curve.moved(index, CurvePoint(start.x, start.y + DRAG * (step + 1) / 4f), LIMIT)
            gesture.update(SetOutline(id, view, last))
        }
        gesture.commit()
        runCurrent()
        assertEquals(1, session.history.steps.value.done.size, "one drag is one undo step")
        val after = session.body()
        val sections = assertNotNull(curve.changedSections(last))
        assertLocal(before, after, listOf(reach(curve, sections), reach(last, sections)))
        fixture(name, after)
        session.history.undo()
        runCurrent()
        assertEquals(emptySet(), changedRings(before, session.body()), "undo restores every ring")
    }

    private fun TestScope.overdraw(view: View, curve: OutlineCurve, height: Float, name: String) {
        val session = drawnSession()
        val before = session.body()
        val result = assertNotNull(oversketchedOutline(curve, bump(height), PLANE, PIXEL))
        assertEquals(curve.points.take(result.firstSection * 3 + 1), result.curve.points.take(result.firstSection * 3 + 1))
        val addedSections = result.curve.sections - curve.sections + result.replaced
        assertEquals(
            curve.points.takeLast((curve.sections - result.firstSection - result.replaced) * 3 + 1),
            result.curve.points.takeLast((curve.sections - result.firstSection - result.replaced) * 3 + 1),
        )
        session.send(SetOutline(id, view, result.curve))
        runCurrent()
        val after = session.body()
        val touched = listOf(
            reach(curve, result.firstSection until result.firstSection + result.replaced),
            reach(result.curve, result.firstSection until result.firstSection + addedSections),
        )
        assertLocal(before, after, touched)
        fixture(name, after)
    }

    @Test
    fun aHandleDragInSideMovesOnlyItsSpan() = runTest { dragHandle(View.SIDE, side, "side-handle") }

    @Test
    fun aHandleDragInTopMovesOnlyItsSpan() = runTest { dragHandle(View.TOP, top, "top-handle") }

    @Test
    fun anOversketchInSideMovesOnlyItsSpan() = runTest { overdraw(View.SIDE, side, SIDE_HEIGHT, "side-oversketch") }

    @Test
    fun anOversketchInTopMovesOnlyItsSpan() = runTest { overdraw(View.TOP, top, TOP_WIDTH, "top-oversketch") }

    @Test
    fun movingTheFarEndMovesEveryRingAlongTheAxis() = runTest {
        val session = drawnSession()
        val before = session.body()
        val tip = side.anchors.maxBy { side.points[it].x }
        val point = side.points[tip]
        session.send(SetOutline(id, View.SIDE, side.moved(tip, CurvePoint(point.x + 0.2f, point.y), LIMIT)))
        runCurrent()
        val changed = changedRings(before, session.body())
        assertEquals((1 until before.nu).toSet(), changed - 0, "the ring count is fixed, so every ring past the socket shifts")
    }

    @Test
    fun aFrontSectionMovesEveryRing() = runTest {
        val torso = template("torso")
        assertEquals(dev.chieppa.squash.character.model.Sets.SECTION, torso.views[View.FRONT], "the torso's Front sets its section")
        val square = outline(-0.5f to -0.5f, 0.5f to -0.5f, 0.5f to 0.5f, -0.5f to 0.5f, -0.5f to -0.5f)
        val instance = PartInstance(id, TemplateRef(TemplateId("torso"), torso.version), own = PartData(outlines = mapOf(View.SIDE to side)))
        val session = session(instance)
        session.mesh(id)
        runCurrent()
        val before = session.body()
        session.send(SetOutline(id, View.FRONT, square))
        runCurrent()
        assertEquals((1 until before.nu - 1).toSet(), changedRings(before, session.body()) - setOf(0, before.nu - 1), "every ring but the closed ends' points")
    }
}
