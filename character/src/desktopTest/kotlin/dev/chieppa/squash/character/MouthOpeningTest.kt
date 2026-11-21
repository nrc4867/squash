package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.BACK_TINT
import dev.chieppa.squash.character.build.OpenMouths
import dev.chieppa.squash.character.build.SurfaceWorkInput
import dev.chieppa.squash.character.build.openMouths
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.character.build.resolveSurfaceWork
import dev.chieppa.squash.character.build.surfaceOf
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.SurfaceUv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val HEAD = PartId("head")
private val MOUTH = MarkGroupId("mouth")
private val INNER = SlotName("mouth")
private val TONGUE = SlotName("tongue")
private val TOOTH = SlotName("tooth")

/** How far the lower lip may sit from where the open puts it, as a share of the lip line's width. */
private const val DROP_SLACK = 0.08f

/** A mouth on a jawless round head opened by parting its lips on the surface: the opening, tongue and teeth, kept between the lips. */
class MouthOpeningTest {
    private val template = shipped.parts.single { it.id.value == "head-round" && it.version == 2 }
    private val surface = surfaceOf(template, PartData(), mirrored = false)

    private fun on(u: Float, v: Float) = Anchor(HEAD, BODY_PATCH, u, v)

    /** A lip line across the face's front, crossing v 0 where the head's v wraps. */
    private val lip = Mark(MarkId("lip"), listOf(on(0.26f, 0.95f), on(0.25f, 0.98f), on(0.25f, 0.02f), on(0.26f, 0.05f)))

    /** A fang drawn longer than a half-open mouth is deep. */
    private val fang = Mark(MarkId("fang"), listOf(on(0.252f, 0.985f), on(0.252f, 0.995f), on(0.19f, 0.99f)))

    private val tongue = Mark(MarkId("tongue"), listOf(on(0.23f, 0.98f), on(0.22f, 0f), on(0.23f, 0.02f), on(0.24f, 0f)), ink = false)

    private fun opened(open: Float): OpenMouths {
        val group = MarkGroup(
            MOUTH, "Mouth",
            listOf(
                GroupRole(MarkRole.INNER, ink = false, fill = INNER),
                GroupRole(MarkRole.TONGUE, ink = false, fill = TONGUE, marks = listOf(tongue.id)),
                GroupRole(MarkRole.TEETH, fill = TOOTH, marks = listOf(fang.id)),
                GroupRole(MarkRole.LIP_LINE, marks = listOf(lip.id)),
            ),
            MarkControls(open = open),
        )
        val work = resolveSurfaceWork(SurfaceWorkInput(SurfaceWork(marks = listOf(lip, fang, tongue), groups = listOf(group)), parts = setOf(HEAD)), mapOf(HEAD to surface))
        return openMouths(HEAD, work, mapOf(HEAD to surface))
    }

    private fun bodyY(place: SurfaceUv) = checkNotNull(surface.pointAt(on(place.u, place.v))).position.y

    private val lipWidth: Float by lazy {
        val ends = listOf(lip.points.first(), lip.points.last()).map { checkNotNull(surface.pointAt(it)).position }
        (ends[0] - ends[1]).length
    }

    /** How far the lower lip's middle sits below the lip line's, in body units. */
    private fun drop(mouth: OpenMouths): Float {
        val lower = mouth.lines.single { it.id.value == "mouth-lower-lip" }.points
        val upper = checkNotNull(surface.pointAt(on(0.25f, 0f))).position.y
        return upper - lower[lower.size / 2].position.y
    }

    @Test
    fun aClosedMouthAddsNothing() {
        assertEquals(OpenMouths.NONE, opened(0f))
    }

    @Test
    fun anOpenMouthFillsItsOpeningTongueAndTeethInOrderAndInksItsLowerLip() {
        val mouth = opened(1f)
        assertEquals(listOf(INNER, INNER, TONGUE, TOOTH), mouth.shapes.map { it.slot }, "the opening, then its carved back")
        assertEquals(listOf(null, BACK_TINT), mouth.shapes.take(2).map { it.tint }, "the back is the opening's fill taken darker")
        assertTrue(mouth.shapes.all { it.part == HEAD && it.patch == BODY_PATCH })
        val teeth = mouth.shapes.last().shape as ColorShape.Border
        assertEquals(teeth.points.indices.toSet(), teeth.corners, "a tooth is sharp at every place")
        assertTrue(mouth.lines.any { it.id.value == "mouth-lower-lip" })
    }

    @Test
    fun theLowerLipDropsByTheOpenFromTheLipLine() {
        val full = drop(opened(1f))
        val half = drop(opened(0.5f))
        assertEquals(0.45f * lipWidth, full, DROP_SLACK * lipWidth, "a full open drops the lower lip 0.45 of the lip line's width")
        assertEquals(full / 2f, half, DROP_SLACK * lipWidth, "half open drops it half as far")
    }

    @Test
    fun teethStayBetweenTheLips() {
        val mouth = opened(0.3f)
        val lowest = mouth.lines.single { it.id.value == "mouth-lower-lip" }.points.minOf { it.position.y }
        val teeth = mouth.shapes.single { it.slot == TOOTH }.shape as ColorShape.Border
        assertTrue(teeth.points.all { bodyY(it) >= lowest - 1e-3f }, "a long fang is cut at the lower lip of a mouth a third open")
    }
}
