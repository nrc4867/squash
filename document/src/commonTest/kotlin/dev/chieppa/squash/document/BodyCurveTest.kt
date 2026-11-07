package dev.chieppa.squash.document

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BodyCurveTest {
    private val side = BodyStroke(View.SIDE, listOf(CurvePoint(0f, 0f), CurvePoint(0.3f, 0.2f), CurvePoint(0.6f, 0.2f), CurvePoint(0.9f, 0f)))
    private val top = side.copy(view = View.TOP)

    @Test
    fun aPoseKeepsItsCurveThroughJson() {
        val pose = Pose(listOf(BonePose(PartId("torso"), "waist", 12f)), setOf(PartId("front-paw-l")), BodyCurve(listOf(side, top), 0.92f))
        assertEquals(pose, Json.decodeFromString(Pose.serializer(), Json.encodeToString(Pose.serializer(), pose)))
        assertEquals(top, pose.curve?.stroke(View.TOP))
    }

    @Test
    fun aPoseWithNoCurveSavesAsBefore() {
        val text = Json.encodeToString(Pose.serializer(), Pose(listOf(BonePose(PartId("tail"), "root", 20f))))
        assertFalse("curve" in text)
    }

    @Test
    fun aCurveHoldsOneStrokeAViewInSideOrTop() {
        assertFailsWith<IllegalArgumentException> { BodyCurve(listOf(side, side)) }
        assertFailsWith<IllegalArgumentException> { side.copy(view = View.FRONT) }
        assertFailsWith<IllegalArgumentException> { BodyStroke(View.SIDE, side.points.take(3)) }
        assertFailsWith<IllegalArgumentException> { BodyCurve(listOf(side), 0f) }
    }

    @Test
    fun aPoseChangeKeepsTheCurve() {
        val pose = Pose(curve = BodyCurve(listOf(side)))
        assertEquals(pose.curve, pose.with(BonePose(PartId("tail"), "root", 5f)).withPlanted(PartId("front-paw-l"), true).curve)
    }
}
