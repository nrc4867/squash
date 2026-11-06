package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.rememberSceneSurface

/**
 * Every screen but Home, in one Build frame that stays up as the screen changes, with one native surface they share. Only
 * the viewport and the drawers that differ change. Focus comes back here on each change of screen or tool, since the inspector
 * changes with the tool, because what held it may be gone and keys need a focused element to reach the shortcuts.
 */
@Composable
fun BuildScreens(shell: Shell, screen: Screen) {
    val home = remember { FocusRequester() }
    val tool by shell.state.tool.collectAsState()
    LaunchedEffect(screen, tool) { home.requestFocus() }
    val tagged = if (screen == Screen.WORKSPACE) Modifier.testTag(ShellTags.WORKSPACE) else Modifier
    CompositionLocalProvider(LocalSceneSurface provides rememberSceneSurface()) {
        BuildFrame(shell, screen, tagged) {
            Box(Modifier.fillMaxSize().focusRequester(home).focusable()) {
                if (screen == Screen.NEW_CHARACTER) NewCharacterScreen(shell) else shell.content.workspace(shell)
            }
        }
    }
}
