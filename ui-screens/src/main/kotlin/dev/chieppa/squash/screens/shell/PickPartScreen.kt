package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pick_socket_hud
import dev.chieppa.stretch.frame.Hud
import dev.chieppa.stretch.frame.Viewport
import org.jetbrains.compose.resources.stringResource

/** Pick a part type: the tiles in the bottom panel, the socket from the Parts tree, the type's facts in the Part drawer. */
@Composable
fun PickPartScreen(shell: Shell) {
    val socket by shell.state.pickedSocket.collectAsState()
    BuildFrame(shell, Modifier.testTag(ShellTags.PICK)) {
        Viewport(
            bottomEnd = socket?.let { open ->
                {
                    Box(Modifier.testTag(ShellTags.PICK_SOCKET)) {
                        Hud(stringResource(Res.string.pick_socket_hud, displayName(open.name).lowercase()))
                    }
                }
            },
        ) {}
    }
}
