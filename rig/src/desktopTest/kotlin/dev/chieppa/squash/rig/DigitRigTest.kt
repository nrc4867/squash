package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val HAND_L = PartId("hand-l")
private val HAND_R = PartId("hand-r")
private val BIRD_FOOT_L = PartId("foot-l")

/** How much nearer the palm a whole curl brings each fingertip at least, as a share of its distance at rest. */
private const val CLOSES = 0.3f
private const val STILL = 1e-5f
private const val MIRRORED = 1e-3f
private const val WEB = "web:"

/** Points round each ring of the hand's webs and fingers. */
private const val WEB_SEGMENTS = 16
private const val FINGER_SEGMENTS = 12

/** How much further a web may stretch or shrink under a curl than its own digits' surface does, the skinning's own at a bent joint. */
private const val DIGITS_SHARE = 1.35f

/** How far a web edge may stretch or shrink under a whole curl at least, as a share of its rest length, and how short an edge goes unread. */
private val KEPT = 0.4f..2f
private const val EDGE_FLOOR = 1e-4f

/** The digits of a hand and a bird's foot hang from their wrist as chains of their own, curl toward the underside and grip. */
class DigitRigTest {
    private val person get() = personRig
    private val toucan get() = toucanRig

    private fun Rig.point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    /** The vertices of [patch] on [part] and the one furthest along it, its tip. */
    private fun Rig.tipOf(part: PartId, patch: String): Int {
        val rigged = checkNotNull(part(part))
        return rigged.vertices.indices.filter { rigged.vertices[it].patch == patch }.maxBy { rigged.vertices[it].u }
    }

    /** The digits whose bones a vertex of [patch] may follow: a finger's own, or the two a web between fingers spans. */
    private fun spannedBy(patch: String): Set<String> = when {
        patch.startsWith("fingers:") -> setOf(patch)
        patch.startsWith(WEB) -> patch.removePrefix(WEB).toInt().let { setOf("fingers:$it", "fingers:${it + 1}") }
        else -> emptySet()
    }

    /** The least and most any grid edge of [vertices], a patch of [segments] points a ring, changes in length from [rest] to [posed]. */
    private fun Rig.stretchOf(vertices: List<Int>, segments: Int, rest: FloatArray, posed: FloatArray): ClosedFloatingPointRange<Float> {
        var least = Float.MAX_VALUE
        var most = 0f
        for ((local, v) in vertices.withIndex()) {
            val right = vertices[local / segments * segments + (local + 1) % segments]
            for (w in listOfNotNull(right, vertices.getOrNull(local + segments))) {
                val before = (point(rest, w) - point(rest, v)).length
                if (before < EDGE_FLOOR) continue
                val after = (point(posed, w) - point(posed, v)).length / before
                least = minOf(least, after)
                most = maxOf(most, after)
            }
        }
        return least..most
    }

    @Test
    fun aCurledHandsWebsFollowTheirDigitsWithoutTearingOrSmearing() {
        val hand = checkNotNull(person.part(HAND_L))
        val curled = person.posed(person.curled(Pose(), listOf(HAND_L), 1f)).parts.getValue(HAND_L)
        val byPatch = hand.vertices.indices.groupBy { hand.vertices[it].patch }
        val webs = byPatch.filterKeys { it.startsWith(WEB) }
        assertEquals(4, webs.size, "a web between each pair of neighbouring digits")
        for ((patch, vertices) in webs) {
            val digits = spannedBy(patch)
            val followed = vertices.flatMap { v -> hand.weights.of(v).mapNotNull { (bone, _) -> person.bones[bone].digit?.patch } }.toSet()
            assertEquals(digits, followed, "$patch follows both its digits")
            val fingers = digits.map { byPatch.getValue(it) }
            val fingerStretch = fingers.map { person.stretchOf(it, FINGER_SEGMENTS, hand.rest, curled) }
            val most = maxOf(KEPT.endInclusive, fingerStretch.maxOf { it.endInclusive } * DIGITS_SHARE)
            val least = minOf(KEPT.start, fingerStretch.minOf { it.start } / DIGITS_SHARE)
            val stretch = person.stretchOf(vertices, WEB_SEGMENTS, hand.rest, curled)
            assertTrue(stretch.start >= least && stretch.endInclusive <= most, "$patch stretches $stretch, its digits $fingerStretch")
            val near = { positions: FloatArray, v: Int -> fingers.flatten().minOf { (person.point(positions, it) - person.point(positions, v)).length } }
            vertices.forEach { v ->
                assertTrue(near(curled, v) <= near(hand.rest, v) * most + EDGE_FLOOR, "$patch vertex $v leaves its digits: ${near(hand.rest, v)} to ${near(curled, v)}")
            }
        }
    }

    @Test
    fun aHandsDigitsBranchFromItsWrist() {
        val bones = person.bones.filter { it.name.part == HAND_L }
        assertEquals(1 + 2 + 4 * 3, bones.size)
        assertEquals(listOf("fingers:0.1", "fingers:0.2"), bones.filter { it.digit?.patch == "fingers:0" }.map { it.name.joint })
        assertEquals(BoneName(HAND_L, "wrist"), person.bone(BoneName(HAND_L, "fingers:3.1"))?.parent)
        assertEquals(BoneName(HAND_L, "fingers:3.2"), person.bone(BoneName(HAND_L, "fingers:3.3"))?.parent)
        assertTrue(person.stretchedBy[checkNotNull(person.indexOf(BoneName(HAND_L, "wrist")))] < 0, "no stretch of the wrist reaches a finger")
    }

    @Test
    fun noDigitFollowsAnotherDigitsBones() {
        val hand = checkNotNull(person.part(HAND_L))
        for ((vertex, address) in hand.vertices.withIndex()) {
            val digits = hand.weights.of(vertex).mapNotNull { (bone, _) -> person.bones[bone].digit?.patch }.toSet()
            assertTrue(digits.all { it in spannedBy(address.patch) }, "$address follows $digits")
        }
    }

    @Test
    fun aWholeCurlClosesEveryFingerTowardThePalmAndLeavesThePalmStill() {
        val hand = checkNotNull(person.part(HAND_L))
        val curled = person.posed(person.curled(Pose(), listOf(HAND_L), 1f)).parts.getValue(HAND_L)
        val palm = hand.vertices.indices.filter { hand.vertices[it].patch == "palm" }
        palm.forEach { assertTrue((person.point(curled, it) - person.point(hand.rest, it)).length < STILL, "palm vertex $it moved") }
        val middle = palm.map { person.point(hand.rest, it) }.reduce(Vec3::plus) * (1f / palm.size)
        val under = palm.filter { hand.vertices[it].v in 0.45f..0.55f }.map { person.point(hand.rest, it) - middle }.reduce(Vec3::plus).normalized()
        for (digit in 0 until 5) {
            val tip = person.tipOf(HAND_L, "fingers:$digit")
            val rest = person.point(hand.rest, tip)
            val moved = person.point(curled, tip)
            assertTrue((moved - rest) dot under > 0f, "finger $digit curls toward the palm's underside")
            assertTrue((moved - middle).length < (rest - middle).length * (1f - CLOSES) || digit == 0, "finger $digit closes on the palm")
        }
        assertEquals(1f, person.curlOf(person.curled(Pose(), listOf(HAND_L), 1f), HAND_L) ?: 0f, STILL)
        assertEquals(0f, person.curlOf(Pose(), HAND_L) ?: -1f)
    }

    @Test
    fun aCurledPairIsMirrored() {
        val posed = person.posed(person.curled(Pose(), listOf(HAND_L, HAND_R), 0.6f)).parts
        val left = posed.getValue(HAND_L)
        val right = posed.getValue(HAND_R)
        val plane = (checkNotNull(person.part(HAND_L)).rest[2] + checkNotNull(person.part(HAND_R)).rest[2]) / 2f
        for (v in 0 until left.size / 3) {
            assertEquals(left[v * 3], right[v * 3], MIRRORED)
            assertEquals(left[v * 3 + 1], right[v * 3 + 1], MIRRORED)
            assertEquals(left[v * 3 + 2] - plane, plane - right[v * 3 + 2], MIRRORED)
        }
    }

    @Test
    fun aBirdFootGripsWithItsHindToe() {
        val foot = checkNotNull(toucan.part(BIRD_FOOT_L))
        val gripped = toucan.posed(toucan.curled(Pose(), listOf(BIRD_FOOT_L), 1f)).parts.getValue(BIRD_FOOT_L)
        val tips = (0 until 4).map { toucan.tipOf(BIRD_FOOT_L, "toes:$it") }
        tips.forEach { tip ->
            assertTrue(toucan.point(gripped, tip).y < toucan.point(foot.rest, tip).y, "every toe's tip goes down")
        }
        val gap = { positions: FloatArray -> (toucan.point(positions, tips[3]) - toucan.point(positions, tips[1])).length }
        assertTrue(gap(gripped) < gap(foot.rest), "the hind toe and the middle toe close on each other")
    }
}
