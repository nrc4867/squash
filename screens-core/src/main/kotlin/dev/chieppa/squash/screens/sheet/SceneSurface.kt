package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.stretch.gpu.GpuSurface

/** Draws a frame into the Build frame's one GPU surface, placed by the modifier. */
typealias SceneSurfaceContent = @Composable (frame: SceneFrame?, modifier: Modifier) -> Unit

/** The surface [rememberSceneSurface] made for the screens under it. */
val LocalSceneSurface = staticCompositionLocalOf<SceneSurfaceContent> { error("No scene surface above this composable") }

/**
 * One GPU surface for the screens to share. Whichever screen calls [SceneSurface] takes it over within the same
 * composition, so the GPU device and the uploaded meshes carry across the switch instead of being made again. The surface
 * is drawn by Compose, so the panes' corners, popups and menus draw over it and move with it.
 */
@Composable
fun rememberSceneSurface(): SceneSurfaceContent {
    return remember {
        movableContentOf { frame: SceneFrame?, modifier: Modifier ->
            val look = rememberSheetLook()
            var shown by remember { mutableStateOf(frame ?: SceneFrame(look.well)) }
            val current = frame ?: shown
            SideEffect { shown = current }
            GpuSurface(current, modifier, onFailure = { it.printStackTrace() }) { ViewsScene(it) }
        }
    }
}

/** Shows [frame] in the shared surface. A null frame, from a screen not measured yet, keeps the surface's last picture. */
@Composable
fun SceneSurface(frame: SceneFrame?, modifier: Modifier = Modifier) = LocalSceneSurface.current(frame, modifier)
