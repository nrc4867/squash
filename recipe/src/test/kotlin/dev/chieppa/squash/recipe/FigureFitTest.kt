package dev.chieppa.squash.recipe

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.figure.BoneSide
import dev.chieppa.squash.recipe.figure.BuiltRadii
import dev.chieppa.squash.recipe.figure.FigureInput
import dev.chieppa.squash.recipe.figure.FigureKind
import dev.chieppa.squash.recipe.figure.FigurePartSpec
import dev.chieppa.squash.recipe.figure.FigureStroke
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.fitFigure
import dev.chieppa.squash.recipe.figure.project
import dev.chieppa.squash.recipe.figure.readAt
import dev.chieppa.squash.recipe.figure.samples
import dev.chieppa.squash.recipe.model.RecipeRest
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The figure fit on invented outlines: the split among bones, profiles read in a limb's own frame, roots on the drawn middle, the fills. */
class FigureFitTest {
    private val trunk = FigurePartSpec("trunk", "trunk", FigureKind.TRUNK, length = 0.5, closed = true, rest = RecipeRest.UP)

    /** A left limb on the trunk at height 0.4 of body space, 0.1 out, resting 45 degrees down and out. */
    private fun limb(reach: Double?) = FigurePartSpec(
        "limbs", "limb-l", FigureKind.LIMB, length = 0.3, closed = false, rest = RecipeRest.DOWN, outward = 45.0,
        parent = "trunk", socketU = 0.8, socketV = 0.75, reach = reach,
    )

    private val rootHeight = 0.2

    /** The limb drawn hanging 30 degrees from straight down, from a root at (0.1 + [shift], 0.6) in the front view, [outer] and [inner] wide. */
    private fun hanging(outer: Double, inner: Double, shift: Double = 0.0): List<FigureStroke> {
        val dir = P2(sin(Math.PI / 6), -cos(Math.PI / 6))
        val across = P2(-dir.y, dir.x)
        fun along(t: Double, r: Double) = P2(0.1 + shift, 0.6) + dir * t + across * r
        return listOf(
            FigureStroke.through("outer", listOf(0.0, 0.1, 0.2, 0.3).map { along(it, outer) }),
            FigureStroke.through("inner", listOf(0.3, 0.2, 0.1, 0.03).map { along(it, -inner) }),
        )
    }

    private val drawnPose = listOf(BonePose(PartId("limb-l"), "base", lean = -15f))

    @Test
    fun aStrokesSamplesAreEvenlySpacedWithTheirNormalsOnItsLeft() {
        val samples = FigureStroke.through("s", listOf(P2(0.1, 1.0), P2(0.1, 0.5), P2(0.1, 0.0))).samples(0.01)
        assertEquals(101, samples.size)
        assertEquals(0.5, samples[50].at.y, 1e-3)
        assertEquals(1.0, samples[50].normal.x, 1e-6)
    }

    @Test
    fun aLimbDrawnHangingIsReadInItsOwnFrame() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to hanging(0.03, 0.02)), drawnPose, rootHeight, listOf(trunk, limb(0.1))))
        val limb = fit.parts.getValue("limbs")
        for (x in listOf(0.05, 0.15, 0.25)) {
            assertEquals(0.03, readAt(limb.radii.getValue("outer"), x), 1e-3, "outer at $x")
            assertEquals(0.02, readAt(limb.radii.getValue("inner"), x), 1e-3, "inner at $x")
        }
        assertTrue(fit.split.getValue(FigureView.FRONT).taken.all { it.part == "limbs" }, "the trunk takes none of the limb's outline")
        assertTrue(limb.places.getValue("outer").size <= 4, "a straight side is a few places: ${limb.places}")
    }

    @Test
    fun aLimbTheSideViewCannotShowIsAsDeepAsItIsWide() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to hanging(0.03, 0.02)), drawnPose, rootHeight, listOf(trunk, limb(0.1))))
        val limb = fit.parts.getValue("limbs")
        assertEquals(0.025, readAt(limb.radii.getValue("front"), 0.15), 1e-3)
        assertEquals(0.025, readAt(limb.radii.getValue("back"), 0.15), 1e-3)
    }

    @Test
    fun aRootGivenNoReachIsPlacedOnTheLimbsDrawnMiddle() {
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to hanging(0.025, 0.025, shift = 0.012)), drawnPose, rootHeight, listOf(trunk, limb(null))))
        val limb = fit.parts.getValue("limbs")
        assertEquals(0.112, limb.reach!!, 2e-3, "the root lies where the drawn limb's middle starts")
        assertEquals(0.75, limb.v!!, 1e-3, "straight out to the left")
        assertEquals(0.025, readAt(limb.radii.getValue("outer"), 0.15), 1e-3)
    }

    @Test
    fun anInnerSideHiddenAtItsRootKeepsItsRatioToTheOuter() {
        val dir = P2(sin(Math.PI / 6), -cos(Math.PI / 6))
        val across = P2(-dir.y, dir.x)
        fun along(t: Double, r: Double) = P2(0.1, 0.6) + dir * t + across * r
        val strokes = listOf(
            FigureStroke.through("outer", listOf(0.0, 0.1, 0.2, 0.3).map { along(it, 0.03 - 0.03 * it) }),
            FigureStroke.through("inner", listOf(0.3, 0.2, 0.12).map { along(it, -(0.02 - 0.02 * it)) }),
        )
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to strokes), drawnPose, rootHeight, listOf(trunk, limb(0.1))))
        val inner = fit.parts.getValue("limbs").radii.getValue("inner")
        assertEquals(0.0, inner.first().x, 1e-9, "the inner side runs from the root")
        assertEquals(0.02, readAt(inner, 0.0), 1.5e-3, "two thirds of the outer side there, as where it shows")
    }

    @Test
    fun aClosedTrunkEndUnderAPartStandingOnItStopsReadingUnderThatPartsFillet() {
        val neck = FigurePartSpec("neck", "neck", FigureKind.TRUNK, length = 0.1, closed = false, rest = RecipeRest.UP, parent = "trunk", socketU = 1.0, reach = 0.0)
        // The trunk 0.1 wide up to 0.6, sweeping in over its shoulder to a neck 0.03 wide that runs on up.
        val front = FigureStroke.through("side", listOf(P2(0.03, 0.85), P2(0.03, 0.75), P2(0.06, 0.7), P2(0.1, 0.62), P2(0.1, 0.4), P2(0.1, 0.25)))
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to listOf(front)), emptyList(), rootHeight, listOf(trunk, neck)))
        val left = fit.parts.getValue("trunk").radii.getValue("left")
        assertTrue(left.last().x < 0.5 - 0.01, "the trunk stops reading under the neck's fillet below its top: ${left.last()}")
        assertEquals(0.1, readAt(left, 0.3), 3e-3)
        assertEquals(0.03, readAt(fit.parts.getValue("neck").radii.getValue("left"), 0.06), 2e-3)
    }

    @Test
    fun legsDrawnMeetingInTheMiddleAreOneAboveTheArchAndOpenAsDrawnBelowIt() {
        val legs = FigurePartSpec(
            "legs", "leg-l", FigureKind.LIMB, length = 0.33, closed = false, rest = RecipeRest.DOWN,
            parent = "trunk", socketU = 0.1, socketV = 0.75, reach = 0.05,
        )
        // The trunk's origin at 0.4, the leg's root 0.05 above it and 0.05 out; its inner side drawn up to the middle at 0.36.
        val front = listOf(
            FigureStroke.through("outer", listOf(P2(0.08, 0.45), P2(0.08, 0.3), P2(0.08, 0.13))),
            FigureStroke.through("inner", listOf(P2(0.03, 0.13), P2(0.028, 0.25), P2(0.02, 0.32), P2(0.008, 0.352), P2(0.0, 0.36)), mirrored = true),
        )
        val fit = fitFigure(FigureInput(mapOf(FigureView.FRONT to front), emptyList(), 0.4, listOf(trunk, legs)))
        val inner = fit.parts.getValue("legs").radii.getValue("inner")
        for (x in listOf(0.0, 0.03, 0.06)) {
            assertTrue(readAt(inner, x) > 0.05 + 0.004, "above the arch the leg reaches past the middle: ${readAt(inner, x)} at $x")
        }
        assertEquals(0.05 - 0.028, readAt(inner, 0.2), 2e-3, "below it the leg's inner side follows the drawing")
        assertEquals(0.05 - 0.02, readAt(inner, 0.13), 3e-3)
    }
    @Test
    fun aKeysStrokeUnderALimbRaisedForwardGoesToTheLimbWhicheverWayItIsDrawn() {
        val arm = FigurePartSpec("limbs", "limb-l", FigureKind.LIMB, length = 0.3, closed = false, rest = RecipeRest.DOWN, parent = "trunk", socketU = 0.8, socketV = 0.75, reach = 0.1)
        val forward = listOf(BonePose(PartId("limb-l"), "base", degrees = -100f))
        // Raised a little past level; its underside drawn from near the trunk out along it, the body on the stroke's left: the wrong way round.
        val underarm = FigureStroke.through("underarm", listOf(P2(0.1, 0.57), P2(0.18, 0.57), P2(0.26, 0.57)))
        val built = BuiltRadii { part, _, _ -> if (part == "trunk") 0.08 else 0.03 }
        val input = FigureInput(mapOf(FigureView.SIDE to listOf(underarm)), forward, rootHeight, listOf(trunk, arm), built = built)
        val split = fitFigure(input).split.getValue(FigureView.SIDE)
        assertTrue(split.unread.isEmpty(), "every sample is read: ${split.unread.size} unread")
        assertTrue(split.taken.all { it.part == "limbs" }, "the limb's underside goes to the limb: ${split.taken.map { it.part }.toSet()}")
        assertTrue(split.taken.all { it.sample.normal.y < -0.9 }, "the stroke faces out of the limb")
        assertTrue(split.taken.all { it.side == BoneSide.MINUS }, "raised past level, the limb's underside is still the side its back faces at rest")
        val plain = fitFigure(FigureInput(mapOf(FigureView.SIDE to listOf(underarm)), forward, rootHeight, listOf(trunk, arm))).split.getValue(FigureView.SIDE)
        assertTrue(plain.taken.none { it.part == "limbs" }, "a figure's split, reading no built body, does not read the limb from the side")
    }

    @Test
    fun aKeysStrokeInsideABentJointIsInItsCreaseAndOneRoundTheOutsideIsNot() {
        val leg = FigurePartSpec(
            "legs", "leg-l", FigureKind.LIMB, length = 0.3, closed = false, rest = RecipeRest.DOWN, joints = listOf("joint.1" to 0.5),
            parent = "trunk", socketU = 0.0, socketV = 0.5, reach = 0.05,
        )
        val bent = listOf(BonePose(PartId("leg-l"), "joint.1", degrees = 90f))
        val built = BuiltRadii { part, _, _ -> if (part == "trunk") 0.08 else 0.03 }
        val chain = fitFigure(FigureInput(emptyMap(), bent, rootHeight, listOf(trunk, leg), built = built)).posed.getValue("legs")
        val (hip, knee, ankle) = chain.joints.map { project(FigureView.SIDE, it.at, rootHeight) }
        val up = (hip - knee).unit()
        val on = (ankle - knee).unit()
        fun near(a: Double, b: Double) = knee + up * a + on * b
        val crease = FigureStroke.through("crease", listOf(near(0.045, 0.03), near(0.037, 0.032), near(0.032, 0.037), near(0.03, 0.045)))
        val outside = FigureStroke.through("outside", listOf(near(0.06, -0.03), near(-0.02, -0.035), near(-0.035, -0.02), near(-0.03, 0.06)))
        val input = FigureInput(mapOf(FigureView.SIDE to listOf(crease, outside)), bent, rootHeight, listOf(trunk, leg), built = built)
        val split = fitFigure(input).split.getValue(FigureView.SIDE)
        val inCrease = split.taken.filter { it.sample.stroke == "crease" }
        assertTrue(inCrease.isNotEmpty() && inCrease.all { it.part == "legs" }, "the crease goes to the leg: ${inCrease.map { it.part }.toSet()}")
        assertTrue(inCrease.all { it.crease }, "every sample inside the bend lies in its crease: ${inCrease.map { it.crease }}")
        assertTrue(split.taken.filter { it.sample.stroke == "outside" }.none { it.crease }, "no sample round the outside does")
        val plain = fitFigure(FigureInput(mapOf(FigureView.SIDE to listOf(crease)), bent, rootHeight, listOf(trunk, leg))).split.getValue(FigureView.SIDE)
        assertTrue(plain.taken.none { it.crease }, "a figure's split, reading no built body, finds no crease")
    }
}
