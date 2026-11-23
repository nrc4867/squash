package dev.chieppa.squash.recipe

import dev.chieppa.squash.recipe.figure.FigureInput
import dev.chieppa.squash.recipe.figure.FigureKind
import dev.chieppa.squash.recipe.figure.FigurePartSpec
import dev.chieppa.squash.recipe.figure.FigureStroke
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.fitFigure
import dev.chieppa.squash.recipe.figure.profileName
import dev.chieppa.squash.recipe.figure.BoneSide
import dev.chieppa.squash.recipe.figure.readAt
import dev.chieppa.squash.recipe.model.RecipeRest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The figure fit on a four-legged body: a trunk lying level read from the side and from above, legs behind legs read from the side alone. */
class LevelFigureFitTest {
    private val trunk = FigurePartSpec("trunk", "trunk", FigureKind.TRUNK, length = 0.6, closed = true, rest = RecipeRest.FORWARD)

    private fun legs(id: String, u: Double) = FigurePartSpec(
        id, "$id-l", FigureKind.LIMB, length = 0.3, closed = false, rest = RecipeRest.DOWN, parent = "trunk", socketU = u, socketV = 0.6, reach = 0.06,
    )

    private val rootHeight = 0.4

    /** The trunk drawn 0.1 deep above its axis, 0.12 below it, from the side; 0.09 wide from above; each stroke with the body on its right. */
    private val side = listOf(
        FigureStroke.through("back", listOf(P2(0.05, 0.5), P2(0.3, 0.5), P2(0.55, 0.5))),
        FigureStroke.through("belly", listOf(P2(0.48, 0.28), P2(0.3, 0.28), P2(0.12, 0.28))),
    )
    private val top = listOf(FigureStroke.through("flank", listOf(P2(0.09, 0.55), P2(0.09, 0.3), P2(0.09, 0.05))))

    @Test
    fun aLevelTrunksSidesAreNamedUpAndDownFromTheSideAndLeftFromAbove() {
        assertEquals("up", profileName(trunk, FigureView.SIDE, BoneSide.PLUS))
        assertEquals("down", profileName(trunk, FigureView.SIDE, BoneSide.MINUS))
        assertEquals("left", profileName(trunk, FigureView.TOP, BoneSide.PLUS))
        val upright = trunk.copy(rest = RecipeRest.UP)
        assertEquals("front", profileName(upright, FigureView.SIDE, BoneSide.PLUS))
        assertEquals("left", profileName(upright, FigureView.FRONT, BoneSide.PLUS))
    }

    @Test
    fun aLevelTrunkTakesItsDepthFromTheSideAndItsWidthFromAbove() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.SIDE to side, FigureView.TOP to top), emptyList(), rootHeight, listOf(trunk)))
        val radii = fit.parts.getValue("trunk").radii
        assertEquals(0.1, readAt(radii.getValue("up"), 0.3), 3e-3)
        assertEquals(0.12, readAt(radii.getValue("down"), 0.3), 3e-3)
        assertEquals(0.09, readAt(radii.getValue("left"), 0.3), 3e-3)
        assertFalse("trunk" in fit.reads.getValue(FigureView.FRONT), "the front view sees a level trunk end on")
    }

    @Test
    fun aLevelTrunkNoViewSeesAcrossIsAsWideAsItIsDeep() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.SIDE to side), emptyList(), rootHeight, listOf(trunk)))
        assertEquals(0.11, readAt(fit.parts.getValue("trunk").radii.getValue("left"), 0.3), 3e-3)
    }

    @Test
    fun legsBehindLegsAreLeftToTheSideViewAndAreAsWideAsTheyAreDeep() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.SIDE to side), emptyList(), rootHeight, listOf(trunk, legs("front-legs", 0.85), legs("back-legs", 0.15))))
        val front = fit.reads.getValue(FigureView.FRONT)
        assertTrue("front-legs" in front)
        assertFalse("back-legs" in front, "the hind legs stand behind the front legs")
        assertTrue("back-legs" in fit.reads.getValue(FigureView.SIDE))
    }
}
