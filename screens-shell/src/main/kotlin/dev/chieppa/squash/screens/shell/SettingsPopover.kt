package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.settings_theme
import dev.chieppa.squash.uidesign.generated.settings_theme_dark
import dev.chieppa.squash.uidesign.generated.settings_theme_light
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** What the top bar's Settings button opens: the app's theme. */
@Composable
fun SettingsPopover(shell: Shell) {
    val dark by shell.state.darkTheme.collectAsState()
    val light = stringResource(Res.string.settings_theme_light)
    val darkName = stringResource(Res.string.settings_theme_dark)
    Popover(shell.settings, { shell.settings = false }) {
        SettingRow(stringResource(Res.string.settings_theme)) {
            Spacer(Modifier.weight(1f))
            Segmented(listOf(light, darkName), if (dark) darkName else light, { shell.state.darkTheme.value = it == darkName }, { it })
        }
    }
}
