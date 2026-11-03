package dev.chieppa.squash

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.shell.RecentStore
import dev.chieppa.squash.screens.shell.ScratchFormat
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.app_name
import dev.chieppa.squash.uidesign.generated.shell_file_type
import dev.chieppa.stretch.devtools.DevTools
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFiles
import dev.chieppa.stretch.document.appDataDirectory
import dev.chieppa.stretch.document.rememberFileChooser
import dev.chieppa.stretch.frame.NativeSurfaces
import dev.chieppa.stretch.frame.StretchWindow
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val TEMPLATES_PROPERTY = "squash.templates"
private const val APP_ID = "dev.chieppa.squash"
private const val RECENT_FILE = "recent.txt"

fun main() {
    DevTools.startIfRequested()
    NativeSurfaces.enable()
    val store = RecentStore(appDataDirectory(APP_ID).resolve(RECENT_FILE))
    val recent = RecentFiles(store.load()).also { it.prune(FileStorage) }
    val state = AppState(
        TemplateFolder.load(Path.of(System.getProperty(TEMPLATES_PROPERTY))),
        recent = recent,
        format = ScratchFormat(runBlocking { getString(Res.string.shell_file_type) }),
    )
    application {
        StretchWindow(
            onCloseRequest = { state.whenSaved(::exitApplication) },
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
        ) {
            LaunchedEffect(recent) {
                snapshotFlow { recent.entries }.drop(1).collect { withContext(Dispatchers.IO) { store.save(it) } }
            }
            StretchTheme(StretchThemes.Ink) {
                SquashApp(state, rememberFileChooser(window))
            }
        }
    }
}
