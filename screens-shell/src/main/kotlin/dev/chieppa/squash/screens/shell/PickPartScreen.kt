package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SceneSurface
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.restPoseCamera
import dev.chieppa.squash.screens.sheet.restPoseFrame
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pick_rest_pose
import dev.chieppa.squash.uidesign.generated.pick_socket_hud
import dev.chieppa.squash.uidesign.generated.pick_socket_hud_left
import dev.chieppa.squash.uidesign.generated.pick_tool_add
import dev.chieppa.squash.uidesign.generated.pick_tool_select
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.stringResource

private val DOT_TARGET = 28.dp
private val PICK_TOOL_SIZE = 34.dp

/**
 * Pick a part type: the rest pose with its open sockets as dots, drawn live in the shared surface, the tiles in the bottom
 * panel, the type's facts in the Part drawer. A dot or a row of the Parts tree picks the socket.
 */
@Composable
fun PickPartScreen(shell: Shell) {
    val state = shell.state
    val socket by state.pickedSocket.collectAsState()
    Box(Modifier.fillMaxSize()) {
        RestPoseSurface(state)
        Viewport(
            nativeContent = true,
            topStart = { PickTools(state) },
            topEnd = { Hud(stringResource(Res.string.pick_rest_pose)) },
            bottomEnd = socket?.let { open ->
                {
                    Box(Modifier.testTag(ShellTags.PICK_SOCKET)) {
                        val name = displayName(open.name).lowercase()
                        Hud(stringResource(if (open.paired) Res.string.pick_socket_hud_left else Res.string.pick_socket_hud, name))
                    }
                }
            },
        ) { SocketDots(state) }
    }
}

/** Select and Add part: what pressing a socket's dot does. */
@Composable
private fun PickTools(state: AppState) {
    val armed by state.pickTool.collectAsState()
    Toolbar {
        PickToolButton(PickTool.SELECT, SquashIcons.Select, stringResource(Res.string.pick_tool_select), "V", armed) { state.pickTool.value = it }
        PickToolButton(PickTool.ADD_PART, SquashIcons.AddPart, stringResource(Res.string.pick_tool_add), "A", armed) { state.pickTool.value = it }
    }
}

@Composable
private fun PickToolButton(tool: PickTool, icon: ImageVector, label: String, key: String, armed: PickTool, onArm: (PickTool) -> Unit) {
    Tooltip(label, listOf(key)) {
        ToolButton(icon, label, tool == armed, { onArm(tool) }, Modifier.testTag(ShellTags.pickTool(tool)), size = PICK_TOOL_SIZE)
    }
}

/** The open character's assembled rest pose, and the sockets still open on it. */
@Composable
private fun rememberRestPose(state: AppState): Pair<AssembledCharacter, List<OpenSocket>>? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val document by remember(open) { open.document }.collectAsState()
    val character by remember(open) { open.assembly.output }.collectAsState()
    val plan = state.templates.plan(document.character.bodyPlan) ?: return null
    val assembled = character ?: return null
    return assembled to openSockets(document, plan, state.templates)
}

/** The rest pose with its open sockets as dots, at the size it is given, in the shared surface. */
@Composable
fun RestPoseSurface(state: AppState) {
    val pose = rememberRestPose(state)
    val picked by state.pickedSocket.collectAsState()
    val look = rememberSheetLook()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = when {
        size == IntSize.Zero -> null
        pose == null -> SceneFrame(look.well)
        else -> restPoseFrame(pose.first, look, size.width, size.height, pose.second.map { it.ref }, picked?.ref)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        SceneSurface(frame, Modifier.fillMaxSize())
    }
}

/** A target over each open socket's dot, which the surface draws. Pressing one picks its socket. */
@Composable
private fun BoxScope.SocketDots(state: AppState) {
    val pose = rememberRestPose(state) ?: return
    var size by remember { mutableStateOf(IntSize.Zero) }
    val half = with(LocalDensity.current) { DOT_TARGET.toPx() / 2f }
    Box(Modifier.matchParentSize().onSizeChanged { size = it })
    val box = pose.first.box ?: return
    if (size == IntSize.Zero) return
    val camera = restPoseCamera(box, size.width, size.height)
    for (socket in pose.second) {
        val placed = pose.first.sockets.firstOrNull { it.ref == socket.ref } ?: continue
        val at = camera.toPixel(placed.position)
        Box(
            Modifier
                .offset { IntOffset((at.x - half).toInt(), (at.y - half).toInt()) }
                .size(DOT_TARGET)
                .testTag(ShellTags.socketDot(socket.name))
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (state.pickTool.value == PickTool.ADD_PART) state.placeOn(socket) else state.chooseSocket(socket)
                },
        )
    }
}
