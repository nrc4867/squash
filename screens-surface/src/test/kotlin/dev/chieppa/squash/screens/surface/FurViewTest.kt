package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.FurCamera
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.furView
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.INK
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.furCamera
import dev.chieppa.squash.screens.sheet.ShownFur
import dev.chieppa.squash.screens.sheet.furDraws
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val VIEW = "test"
private const val WIDE_SPACING = 80f
private const val TIGHT_SPACING = 4f

/** Two strokes this large cannot share the room within half the spacing. */
private const val MOSTLY_GROWN = 0.5f
private val CLOSE = Vec3(0f, 0f, -2.5f)
private val FAR = Vec3(0f, 0f, -10f)

/** A stroke's length varies by under this either way, with perspective on top. */
private const val LENGTH_SLACK = 1.5f

/** Turns of the view a stroke is held to through. */
private const val ORBIT_FRAMES = 12

/** How far a tuft's tip may show from where its fixed point does, in pixels, for rounding. */
private const val TIP_HELD_PIXELS = 0.05f

/** How far a stroke's size may move through an orbit, for rounding. */
private const val SIZE_HELD = 1e-3f

/** How many pixels a body unit spans for the spacing to hold in pixels. */
private const val FULL_SCALE = 280f

/** Strokes this large hold at least this share of the spacing between them, on the body. */
private const val SPACING_HELD = 0.85f

class FurViewTest {
    private val session = openSession(fennecDocument())

    private fun center(part: PartId): Vec3 = assertNotNull(session.assembly.output.value?.part(part)?.box?.center)

    private fun shown(offset: Vec3 = Vec3(0f, 0f, -5f)): Pair<FurView, FurCamera> {
        val camera = furCamera(sideCamera(center(TORSO), offset), VIEW_PIXELS, VIEW_PIXELS)
        session.showFurFrom(VIEW, camera)
        session.settle()
        return assertNotNull(session.furView(VIEW).output.value) to camera
    }

    private fun rule(part: PartId, rule: FurRule) {
        session.send(SetFurRule(part, rule))
        session.settle()
    }

    @Test
    fun largeStrokesStaySpacedOnTheBody() {
        rule(TAIL, FurRule(spacingPixels = WIDE_SPACING))
        val (view, _) = shown()
        val spacing = WIDE_SPACING / FULL_SCALE
        val roots = assertNotNull(view.parts[TAIL]).wedges.filter { it.size > MOSTLY_GROWN }.map { it.root }
        assertTrue(roots.size > 1)
        roots.forEachIndexed { i, a ->
            roots.drop(i + 1).forEach { b -> assertTrue((a - b).length >= spacing * SPACING_HELD, "$a and $b are too close") }
        }
    }

    @Test
    fun aPressOffTheBodyTakesTheNearestPlaceOnItWithinReach() {
        val character = assertNotNull(session.assembly.output.value)
        val camera = sideCamera(center(TAIL), Vec3(3f, 1f, -4f))
        val sight = FullView({ camera }, VIEW_PIXELS, VIEW_PIXELS)
        val middle = assertNotNull(sight.toPixel(center(TAIL)))
        val away = generateSequence(middle) { it + androidx.compose.ui.geometry.Offset(0f, -4f) }.first { surfaceHit(character, it, sight) == null } + androidx.compose.ui.geometry.Offset(0f, -8f)
        val taken = assertNotNull(surfaceHitWithin(character, away, sight, 1f))
        val shown = assertNotNull(sight.toPixel(taken.position))
        assertTrue(hypot(shown.x - away.x, shown.y - away.y) < 24f, "it lies at the edge next to the press")
        assertEquals(null, surfaceHitWithin(character, androidx.compose.ui.geometry.Offset(0f, 0f), sight, 0.01f), "out of reach takes nothing")
    }

    @Test
    fun lengthSetsHowLongATuftStandsOnTheBody() {
        val length = 40f
        rule(TAIL, FurRule(spacingPixels = TIGHT_SPACING, lengthPixels = length))
        val longest = assertNotNull(shown(CLOSE).first.parts[TAIL]).wedges.maxOf { it.run.length }
        assertTrue(longest <= length / FULL_SCALE * LENGTH_SLACK, "the longest is $longest")
        assertTrue(longest >= length / FULL_SCALE / LENGTH_SLACK, "the longest is $longest")
    }

    @Test
    fun aTuftShowsWhereItsFixedTipDoesFromEveryCamera() {
        val fur = assertNotNull(session.bodyFur.output.value)
        val center = center(TORSO)
        val runs = HashMap<String, Vec3>()
        repeat(ORBIT_FRAMES) { frame ->
            val turn = frame * 2.0 * Math.PI / ORBIT_FRAMES
            val offset = Vec3(5f * kotlin.math.sin(turn).toFloat(), 1f, -5f * kotlin.math.cos(turn).toFloat())
            val camera = furCamera(sideCamera(center, offset), VIEW_PIXELS, VIEW_PIXELS)
            furView(fur, camera).parts.values.flatMap { it.wedges }.forEach { wedge ->
                val run = runs.getOrPut(wedge.id.value) { wedge.run }
                assertEquals(0f, (wedge.run - run).length, SIZE_HELD, "${wedge.id.value} at frame $frame")
                val fixed = assertNotNull(camera.toPixel(wedge.root + run))
                val shown = assertNotNull(camera.toPixel(wedge.tip))
                assertTrue(hypot(fixed.x - shown.x, fixed.y - shown.y) < TIP_HELD_PIXELS, "${wedge.id.value} at frame $frame")
            }
        }
        assertTrue(runs.isNotEmpty())
    }

    @Test
    fun anOrbitChangesNoStrokesSize() {
        val center = center(TORSO)
        val fur = assertNotNull(session.bodyFur.output.value)
        val sizes = HashMap<String, Float>()
        val seen = HashSet<String>()
        repeat(ORBIT_FRAMES) { frame ->
            val turn = frame * 2.0 * Math.PI / ORBIT_FRAMES
            val offset = Vec3(5f * kotlin.math.sin(turn).toFloat(), 0f, -5f * kotlin.math.cos(turn).toFloat())
            val camera = furCamera(sideCamera(center, offset), VIEW_PIXELS, VIEW_PIXELS)
            furView(fur, camera).parts.values.flatMap { it.wedges }.forEach { wedge ->
                seen += wedge.id.value
                assertEquals(sizes.getOrPut(wedge.id.value) { wedge.size }, wedge.size, SIZE_HELD, "${wedge.id.value} at frame $frame")
            }
        }
        assertTrue(seen.isNotEmpty())
    }

    @Test
    fun aSmallerViewShrinksAStrokeWithTheBody() {
        rule(TAIL, FurRule(spacingPixels = TIGHT_SPACING, lengthPixels = 40f))
        fun longest(offset: Vec3): Float {
            val (view, camera) = shown(offset)
            return assertNotNull(view.parts[TAIL]).wedges.maxOf { wedge ->
                val base = assertNotNull(camera.toPixel((wedge.left.first() + wedge.right.first()) * 0.5f))
                val tip = assertNotNull(camera.toPixel(wedge.tip))
                hypot(tip.x - base.x, tip.y - base.y)
            }
        }
        assertTrue(longest(FAR) < longest(CLOSE) / 2f, "a view a quarter the scale draws its tufts well under half the length")
    }

    @Test
    fun aStrokeFillsInTheSlotUnderIt() {
        val parts = assertNotNull(session.bodyFur.output.value).parts
        val torso = assertNotNull(parts[TORSO]).roots
        assertEquals(CREAM, torso.first { it.id.value == "chest-8-18" }.slot, "on the cream of the chest")
        assertEquals(FUR, torso.first { it.id.value == "rump-2-5" }.slot, "the torso's base slot")
        val tail = assertNotNull(parts[TAIL]).roots
        assertEquals(FUR, tail.first { it.id.value == "tail-0-0" }.slot, "the tail's base slot")
        val pastTheBorder = Mark(MarkId("past-the-border"), listOf(Anchor(TAIL, BODY_PATCH, 0.9f, 0.5f)), kind = MarkKind.FUR)
        session.send(AddMarks(TAIL, listOf(pastTheBorder)))
        session.settle()
        val tip = assertNotNull(session.bodyFur.output.value?.parts?.get(TAIL)).roots.first { it.id == pastTheBorder.id }
        assertEquals(INK, tip.slot, "the tail's tip, past a border going once round")
    }

    @Test
    fun aViewsCameraLeavesTheCharactersGraphAlone() {
        shown()
        session.showFurFrom(VIEW, furCamera(sideCamera(center(TORSO), Vec3(3f, 1f, -4f)), VIEW_PIXELS, VIEW_PIXELS))
        assertTrue(session.graph.settled.value, "an orbit does not read as a refit")
        session.settle()
    }

    @Test
    fun theFennecsFurIsItsOwnKindOfMark() {
        val marks = assertNotNull(session.document.value.character.resolved(TAIL)).surface.marks
        assertTrue(marks.isNotEmpty() && marks.all { it.kind == MarkKind.FUR && it.points.size == 1 })
    }

    @Test
    fun aPartsInkIsOneRibbonOverItsFills() {
        val (view, _) = shown()
        val draws = furDraws(ShownFur(view, LineRule(2f)), Color.Black) { _, _ -> PartMaterial(Color.White) }
        val parts = view.parts.values
        assertEquals(parts.sumOf { it.fills.size }, draws.layers.sumOf { it.fills.size })
        assertEquals(parts.count { it.wedges.isNotEmpty() }, draws.layers.count { it.ink != null })
        val edges = parts.sumOf { it.wedges.size } * 2
        assertTrue(draws.layers.sumOf { it.ink?.ribbon?.indices?.size ?: 0 } >= edges * 6, "every edge of every wedge is in them")
    }
}
