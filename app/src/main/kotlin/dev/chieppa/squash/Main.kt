package dev.chieppa.squash

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.app_name
import dev.chieppa.stretch.devtools.DevTools
import dev.chieppa.stretch.frame.NativeSurfaces
import dev.chieppa.stretch.frame.StretchWindow
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import java.nio.file.Path
import org.jetbrains.compose.resources.stringResource

private const val TEMPLATES_PROPERTY = "squash.templates"

fun main() {
    DevTools.startIfRequested()
    NativeSurfaces.enable()
    val state = AppState(TemplateFolder.load(Path.of(System.getProperty(TEMPLATES_PROPERTY))))
    application {
        StretchWindow(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
        ) {
            StretchTheme(StretchThemes.Ink) {
                SquashApp(state)
            }
        }
    }
}
