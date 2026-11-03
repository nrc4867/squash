package dev.chieppa.squash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.app_name
import dev.chieppa.stretch.frame.NativeSurfaces
import dev.chieppa.stretch.frame.StretchWindow
import dev.chieppa.stretch.frame.WindowTopBar
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import org.jetbrains.compose.resources.stringResource

fun main() {
    NativeSurfaces.enable()
    application {
        StretchWindow(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
        ) {
            StretchTheme(StretchThemes.Ink) {
                Column(Modifier.fillMaxSize().background(Stretch.colors.surfaceCanvas)) {
                    WindowTopBar(appName = stringResource(Res.string.app_name), document = "")
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}
