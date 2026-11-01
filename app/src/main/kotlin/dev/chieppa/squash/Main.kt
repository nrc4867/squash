package dev.chieppa.squash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import dev.chieppa.stretch.ui.Segmented

private enum class Mode { Build, Pose, Animate, Scene }

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Squash",
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        StretchTheme(StretchThemes.Ink) {
            var mode by remember { mutableStateOf(Mode.Build) }
            val c = Stretch.colors
            Column(Modifier.fillMaxSize().background(c.surfaceCanvas)) {
                Row(
                    Modifier.fillMaxWidth().height(StretchSizes.header).background(c.surfaceRail).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("Squash", style = Stretch.type.heading.merge(TextStyle(color = c.inkStrong)))
                    Segmented(Mode.entries, mode, { mode = it }, { it.name }, mode = true)
                }
                Spacer(Modifier.fillMaxWidth().height(Stretch.stroke).background(c.linePanel))
                Spacer(Modifier.fillMaxSize().background(c.surfaceWell))
            }
        }
    }
}
