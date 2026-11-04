package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.FurCamera
import dev.chieppa.squash.character.build.FurView
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
private const val SPACING_SLACK = 0.49f

/** Two strokes this large cannot share the room within half the spacing. */
private const val MOSTLY_GROWN = 0.5f
private val CLOSE = Vec3(0f, 0f, -2.5f)
private val FAR = Vec3(0f, 0f, -10f)

/** A stroke's length varies by under this either way, with perspective on top. */
private const val LENGTH_SLACK = 1.5f

/** A stroke shrinks away by this cosine past the silhouette. */
private const val PAST_THE_SILHOUETTE = 0.15f

class FurViewTest {
    private val session = openSession(fennecDocument())

    private fun center(part: PartId): Vec3 = assertNotNull(session.assembly.output.value?.part(part)?.box?.center)

    private fun shown(offset: Vec3 = Vec3(0f, 0f, -5f)): Pair<FurView, FurCamera> {
        val camera = furCamera(sideCamera(center(TORSO), offset), VIEW_PIXELS, VIEW_PIXELS, 1f)
        session.showFurFrom(VIEW, camera)
        session.settle()
        return assertNotNull(session.furView(VIEW).output.value) to camera
    }

    private fun rule(part: PartId, rule: FurRule) {
        session.send(SetFurRule(part, rule))
        session.settle()
    }

    @Test
    fun onlyRootsNearTheSilhouetteShow() {
        val (view, camera) = shown()
        val tail = assertNotNull(view.parts[TAIL])
        val rule = assertNotNull(session.document.value.character.resolved(TAIL)?.surface?.fur)
        assertTrue(tail.wedges.isNotEmpty())
        val roots = assertNotNull(session.bodyFur.output.value?.parts?.get(TAIL)).roots
        assertTrue(tail.wedges.size < roots.size, "most of the tail's strokes are not at its edge")
        tail.wedges.forEach { wedge ->
            val facing = wedge.normal dot camera.toViewerFrom(wedge.root)
            assertTrue(facing > -PAST_THE_SILHOUETTE && facing < rule.showNear, "${wedge.id.value} faces the camera by $facing")
        }
    }

    @Test
    fun showNearWidensTheBand() {
        rule(TAIL, FurRule(showNear = 0.1f, spacingPixels = TIGHT_SPACING))
        val narrow = assertNotNull(shown().first.parts[TAIL]).wedges.size
        rule(TAIL, FurRule(showNear = 0.9f, spacingPixels = TIGHT_SPACING))
        val wide = assertNotNull(shown().first.parts[TAIL]).wedges.size
        assertTrue(narrow < wide, "$narrow strokes in a narrow band, $wide in a wide one")
    }

    @Test
    fun largeStrokesStaySpacedOnScreen() {
        rule(TAIL, FurRule(showNear = 0.9f, spacingPixels = WIDE_SPACING))
        val (view, camera) = shown()
        val roots = assertNotNull(view.parts[TAIL]).wedges.filter { it.size > MOSTLY_GROWN }.map { assertNotNull(camera.toPixel(it.root)) }
        assertTrue(roots.size > 1)
        roots.forEachIndexed { i, a ->
            roots.drop(i + 1).forEach { b -> assertTrue(hypot(a.x - b.x, a.y - b.y) >= WIDE_SPACING * SPACING_SLACK, "$a and $b are too close") }
        }
    }

    @Test
    fun lengthSetsHowLongAStrokeShows() {
        val length = 40f
        rule(TAIL, FurRule(showNear = 0.3f, spacingPixels = TIGHT_SPACING, lengthPixels = length))
        val (view, camera) = shown(CLOSE)
        val lengths = assertNotNull(view.parts[TAIL]).wedges.map { wedge ->
            val base = assertNotNull(camera.toPixel((wedge.left.first() + wedge.right.first()) * 0.5f))
            val tip = assertNotNull(camera.toPixel(wedge.tip))
            hypot(tip.x - base.x, tip.y - base.y)
        }
        assertTrue(lengths.max() <= length * LENGTH_SLACK, "the longest is ${lengths.max()} pixels")
        assertTrue(lengths.max() >= length / LENGTH_SLACK, "the longest is ${lengths.max()} pixels")
    }

    @Test
    fun aSmallerViewShrinksAStrokeWithTheBody() {
        rule(TAIL, FurRule(showNear = 0.3f, spacingPixels = TIGHT_SPACING, lengthPixels = 40f))
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
        session.showFurFrom(VIEW, furCamera(sideCamera(center(TORSO), Vec3(3f, 1f, -4f)), VIEW_PIXELS, VIEW_PIXELS, 1f))
        assertTrue(session.graph.settled.value, "an orbit does not read as a refit")
        session.settle()
    }

    @Test
    fun aViewFollowsTheCamera() {
        val side = assertNotNull(shown().first.parts[TAIL]).wedges.map { it.id }.toSet()
        val behind = assertNotNull(shown(Vec3(-5f, 0.5f, -0.5f)).first.parts[TAIL]).wedges.map { it.id }.toSet()
        assertFalse(side == behind, "another camera picks other strokes")
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
        assertEquals(parts.sumOf { it.fills.size }, draws.draws.size)
        assertEquals(parts.count { it.wedges.isNotEmpty() }, draws.ribbons.size)
        val edges = parts.sumOf { it.wedges.size } * 2
        assertTrue(draws.ribbons.sumOf { it.ribbon.indices.size } >= edges * 6, "every edge of every wedge is in them")
    }
}
