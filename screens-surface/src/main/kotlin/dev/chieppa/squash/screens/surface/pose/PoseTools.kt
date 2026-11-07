package dev.chieppa.squash.screens.surface.pose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.stretch.theme.Stretch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The Pose stage's tools, each over the pose the document holds and the rig the graph last derived. */
class PoseTools(val drag: DragTool, val turn: TurnTool, val curve: BodyCurveTool, val pin: PinTool, val look: LookTool) {
    private val all = listOf(drag, turn, curve, pin, look)

    /** The tool [tool] arms, or null for a tool of another stage. */
    fun of(tool: BuildTool): PaneTool? = when (tool) {
        BuildTool.DRAG -> drag
        BuildTool.TURN -> turn
        BuildTool.BODY_CURVE -> curve
        BuildTool.PIN -> pin
        BuildTool.LOOK -> look
        else -> null
    }

    internal fun each(block: (PoseTool) -> Unit) = all.forEach(block)
}

/** The Pose stage's tools for [state], following its document's pose and its rig. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberPoseTools(state: AppState): PoseTools {
    val sheet = rememberSheetLook()
    val c = Stretch.colors
    val look = PoseHandleLook(sheet.ring, c.accent, c.surfaceFloat, c.inkStrong, c.inkMuted, LocalDensity.current.density)
    val tools = remember(state) { PoseTools(DragTool(state, look), TurnTool(state, look), BodyCurveTool(state, look), PinTool(state, look), LookTool(state, look)) }
    val rig = rememberRig(state)
    tools.each {
        it.look = look
        it.rig = rig
    }
    LaunchedEffect(tools, state) {
        state.session.flatMapLatest { open -> open?.document?.map { it.character.pose } ?: flowOf(null) }.collect { pose ->
            if (pose != null) tools.each { it.shownPose = pose }
        }
    }
    return tools
}
