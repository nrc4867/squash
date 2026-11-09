package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.shell.shipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowsPoseTest {
    @Test
    fun poseShowsThePoseInEveryPaneWhateverTheSetting() {
        for (view in PoseView.entries) for (locked in listOf(true, false)) {
            assertTrue(posesPane(Stage.POSE, view, locked), "Pose, $view, locked $locked")
        }
    }

    @Test
    fun restShowsTheRestPoseEverywhereButPose() {
        for (stage in Stage.entries - Stage.POSE) for (locked in listOf(true, false)) {
            assertFalse(posesPane(stage, PoseView.REST, locked), "$stage, locked $locked")
        }
    }

    @Test
    fun posedPosesEveryPaneInRigAndTheFreeCamerasElsewhere() {
        assertTrue(posesPane(Stage.RIG, PoseView.POSED, locked = true))
        assertTrue(posesPane(Stage.RIG, PoseView.POSED, locked = false))
        for (stage in Stage.entries - Stage.POSE - Stage.RIG) {
            assertTrue(posesPane(stage, PoseView.POSED, locked = false), "$stage: Full and Single follow the pose")
            assertFalse(posesPane(stage, PoseView.POSED, locked = true), "$stage: the locked panes stay at rest")
        }
    }

    @Test
    fun theStateReadsFullAsFreeAndAnyOtherCameraAsLockedInEveryLayout() {
        val state = AppState(shipped, work)
        val full = FullView({ error("never looked through") }, 1, 1)
        for (stage in Stage.entries) for (layout in SheetLayout.entries) for (view in PoseView.entries) {
            state.stage.value = stage
            state.layout.value = layout
            state.poseView.value = view
            assertEquals(posesPane(stage, view, locked = false), state.showsPose(full), "$stage $layout $view Full")
            assertEquals(posesPane(stage, view, locked = true), state.showsPose(Locked), "$stage $layout $view locked")
        }
    }

    private val work = TestWork()

    /** Any camera but Full's, as a locked pane is. */
    private object Locked : SurfaceView {
        override fun toPixel(point: Vec3): Offset? = null
        override fun toViewer(point: Vec3): Vec3 = Vec3.Z
        override fun depthOf(point: Vec3): Float = 0f
        override fun pixelsPerUnitAt(point: Vec3): Float = 1f
    }

    @Test
    fun shapeAndDressDrawAtRestInBothSettings() {
        for (stage in listOf(Stage.SHAPE, Stage.DRESS)) for (view in PoseView.entries) {
            assertEquals(false, posesPane(stage, view, locked = true), "$stage $view")
        }
    }
}
