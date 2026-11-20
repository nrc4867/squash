package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.document.FaceStyle
import dev.chieppa.squash.document.KeyFeature
import dev.chieppa.squash.document.KeyMove
import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.ViewKey
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which moves each key direction holds, defaults with authored keys over them, mirrored for the right with twins swapped, and how a move maps. */
class KeyTracksTest {
    private val head = PartId("head")
    private val earL = PartId("ear-l")
    private val earR = PartId("ear-r")

    private val lookup = object : KeyLookup {
        override val heads = listOf(head)
        override fun feature(head: PartId, feature: KeyFeature) = when (feature) {
            KeyFeature.MUZZLE -> listOf(KeyElement(head, "muzzle"))
            KeyFeature.EARS -> listOf(KeyElement(earL), KeyElement(earR))
            KeyFeature.HEAD -> listOf(KeyElement(head))
            KeyFeature.NOSE -> emptyList()
        }
        override fun headOf(part: PartId) = head
        override fun twinOf(part: PartId) = when (part) {
            earL -> earR
            earR -> earL
            else -> null
        }
    }

    private val defaults = listOf(ViewKey(KeyView.THREE_QUARTER, moves = listOf(KeyMove(KeyFeature.MUZZLE, yaw = 10f, left = 0.03f))))

    private fun at(tracks: List<List<ElementMove>>, view: KeyView, side: Side?) = tracks[directionIndex(view, side)].associate { it.element to it.move }

    @Test
    fun theDefaultsMirrorForTheRight() {
        val tracks = keyTracks(ViewKeys(), lookup) { defaults }
        val muzzle = KeyElement(head, "muzzle")
        assertEquals(10f, at(tracks, KeyView.THREE_QUARTER, Side.LEFT).getValue(muzzle).yaw)
        val right = at(tracks, KeyView.THREE_QUARTER, Side.RIGHT).getValue(muzzle)
        assertEquals(-10f, right.yaw)
        assertEquals(-0.03f, right.left)
        assertTrue(at(tracks, KeyView.FRONT, null).isEmpty())
    }

    @Test
    fun anAuthoredKeyReplacesTheDefaultForWhatItMovesAndSwapsTwinsOnTheRight() {
        val keys = ViewKeys(
            keys = listOf(ViewKey(KeyView.THREE_QUARTER, moves = listOf(KeyMove(KeyFeature.MUZZLE, yaw = 20f), KeyMove(part = earL, twist = 30f)))),
        )
        val tracks = keyTracks(keys, lookup) { defaults }
        val left = at(tracks, KeyView.THREE_QUARTER, Side.LEFT)
        assertEquals(20f, left.getValue(KeyElement(head, "muzzle")).yaw)
        assertEquals(0f, left.getValue(KeyElement(head, "muzzle")).left, "the authored move replaces the default whole")
        assertEquals(30f, left.getValue(KeyElement(earL)).twist)
        val right = at(tracks, KeyView.THREE_QUARTER, Side.RIGHT)
        assertEquals(-30f, right.getValue(KeyElement(earR)).twist, "the near ear's key moves to its twin, turned the other way")
        assertTrue(KeyElement(earL) !in right)
    }

    @Test
    fun aKeyForOneSideHoldsThereAloneAndOverTheMirror() {
        val keys = ViewKeys(
            keys = listOf(
                ViewKey(KeyView.SIDE, moves = listOf(KeyMove(KeyFeature.MUZZLE, left = 0.1f))),
                ViewKey(KeyView.SIDE, Side.RIGHT, listOf(KeyMove(KeyFeature.MUZZLE, left = 0.02f))),
            ),
        )
        val tracks = keyTracks(keys, lookup) { emptyList() }
        assertEquals(0.1f, at(tracks, KeyView.SIDE, Side.LEFT).getValue(KeyElement(head, "muzzle")).left)
        assertEquals(0.02f, at(tracks, KeyView.SIDE, Side.RIGHT).getValue(KeyElement(head, "muzzle")).left, "not mirrored")
    }

    @Test
    fun anAreaMoveIsAnElementOfItsOwn() {
        val keys = ViewKeys(keys = listOf(ViewKey(KeyView.THREE_QUARTER, moves = listOf(KeyMove(KeyFeature.HEAD, area = "chin", left = 0.02f), KeyMove(KeyFeature.HEAD, nearPush = 0.03f)))))
        val left = at(keyTracks(keys, lookup) { emptyList() }, KeyView.THREE_QUARTER, Side.LEFT)
        assertEquals(0.02f, left.getValue(KeyElement(head, area = "chin")).left)
        assertEquals(0.03f, left.getValue(KeyElement(head)).nearPush)
    }

    @Test
    fun withoutAutoOnlyAuthoredKeysHold() {
        val tracks = keyTracks(ViewKeys(auto = false), lookup) { defaults }
        assertTrue(tracks.all { it.isEmpty() })
    }

    @Test
    fun aMoveTurnsSlidesAndScalesAboutItsRootInTheHeadsDirections() {
        val frame = HeadFrame.of(Vec3.X, 2f)
        assertEquals(Vec3(0f, 0f, -1f), frame.left)
        val pivot = Vec3(1f, 0f, 0f)
        val tip = KeyAffine.of(KeyMove(KeyFeature.MUZZLE, yaw = 90f), frame, pivot, Vec3.X)(Vec3(2f, 0f, 0f))
        assertTrue((tip - Vec3(1f, 0f, -1f)).length < 1e-5f, "a yaw toward the left turns the front left: $tip")
        val slide = KeyAffine.of(KeyMove(KeyFeature.MUZZLE, left = 0.5f), frame, pivot, Vec3.X)
        assertTrue((slide(pivot) - Vec3(1f, 0f, -1f)).length < 1e-5f, "a slide is a share of the head's length")
        val wide = KeyAffine.of(KeyMove(KeyFeature.HEAD, width = 2f), frame, pivot, Vec3.X)
        assertTrue((wide(Vec3(1f, 0f, 1f)) - Vec3(1f, 0f, 2f)).length < 1e-5f)
        assertTrue((wide(Vec3(2f, 1f, 0f)) - Vec3(2f, 1f, 0f)).length < 1e-5f, "width leaves height and depth")
        val mirrored = KeyMove(KeyFeature.EARS, yaw = 5f, pitch = 3f, roll = 2f, twist = 7f, left = 0.1f).mirrored()
        assertEquals(KeyMove(KeyFeature.EARS, yaw = -5f, pitch = 3f, roll = -2f, twist = -7f, left = -0.1f), mirrored)
    }

    @Test
    fun aFaceFollowsUpToTheSideThenSlidesBackToHide() {
        val turning = FaceFollow.of(ViewKeys(), TemplateId("head-round"))
        assertEquals(FaceStyle.TURNING, turning.style)
        assertTrue(turning.follow in 0.01f..0.99f)
        val fixed = FaceFollow.of(ViewKeys(face = FaceStyle.FIXED), TemplateId("head-round"))
        assertTrue(fixed.keeps && fixed.follow > turning.follow)
        assertEquals(fixed.follow * 90f, fixed.degreesAt(KeyView.SIDE))
        assertEquals(0f, fixed.degreesAt(KeyView.THREE_QUARTER_BACK))
        assertEquals(0f, fixed.degreesAt(KeyView.BACK))
        assertEquals(0f, FaceFollow.of(ViewKeys(auto = false), TemplateId("head")).follow)
        assertEquals(0.5f, FaceFollow.of(ViewKeys(auto = false, faceFollow = 0.5f), TemplateId("head")).follow)
        val frame = HeadFrame.of(Vec3.X, 1f)
        assertEquals(1f, fixed.mask(Vec3.X, frame), 1e-5f)
        assertEquals(0f, fixed.mask(Vec3(-1f, 0f, 0f), frame), 1e-5f)
    }
}
