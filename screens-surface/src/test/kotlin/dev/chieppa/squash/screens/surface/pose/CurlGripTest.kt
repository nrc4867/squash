package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.CurlHandle
import dev.chieppa.squash.rig.PoseHandles
import dev.chieppa.squash.rig.Tail
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A tail's curl handle is pressed on the arc it draws. */
class CurlGripTest {
    /** Body units straight to pixels, one to one, looking down z. */
    private object Flat : SurfaceView {
        override fun toPixel(point: Vec3) = Offset(point.x, point.y)
        override fun toViewer(point: Vec3) = Vec3(0f, 0f, 1f)
        override fun depthOf(point: Vec3) = -point.z
        override fun pixelsPerUnitAt(point: Vec3) = 1f
    }

    private val look = PoseHandleLook(Color.Black, Color.Red, Color.White, Color.Black, Color.Gray, density = 1f)
    private val tail = PartId("tail")
    private val tip = Vec3(100f, 100f, 0f)
    private val handles = PoseHandles(null, emptyMap(), emptyMap(), listOf(CurlHandle(Tail(tail, emptyList()), tip, Vec3(1f, 0f, 0f))), null, null, emptyMap(), emptyList())

    @Test
    fun aPressOnTheArcTakesTheCurlAndOneOnItsBareMiddleDoesNot() {
        val onArc = Offset(tip.x + look.curl, tip.y)
        assertEquals(PoseGrip.Curl(tail), handles.gripAt(onArc, Flat, look) { true })
        assertNull(handles.gripAt(Offset(tip.x, tip.y), Flat, look) { true })
    }
}
