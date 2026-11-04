package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.render.FarSideDarkening
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState

/** The character's stored choice as the renderer takes it. */
fun CharacterShading.objectShading() = ObjectShading(
    mode = when (mode) {
        ShadingMode.FLAT -> Shading.FLAT
        ShadingMode.TWO_TONE -> Shading.TWO_TONE
    },
    farSide = FarSideDarkening(enabled = farSideDarker),
)

/** How the open character is shaded, flat with none open. */
@Composable
fun rememberObjectShading(state: AppState): ObjectShading {
    val session by state.session.collectAsState()
    val open = session ?: return ObjectShading()
    val document by remember(open) { open.document }.collectAsState()
    return remember(document.character.shading) { document.character.shading.objectShading() }
}

/** The stored mode this rendering mode is. */
val Shading.stored: ShadingMode
    get() = when (this) {
        Shading.FLAT -> ShadingMode.FLAT
        Shading.TWO_TONE -> ShadingMode.TWO_TONE
    }

/** Shows the open character in [mode], as one undo step that saves with it. */
fun AppState.chooseShading(mode: Shading) {
    val open = session.value ?: return
    val now = open.document.value.character.shading
    if (now.mode != mode.stored) open.send(SetCharacterShading(now.copy(mode = mode.stored)))
}

/** Turns the open character's far side darkening on or off, as one undo step that saves with it. */
fun AppState.chooseFarSideDarker(on: Boolean) {
    val open = session.value ?: return
    open.send(SetCharacterShading(open.document.value.character.shading.copy(farSideDarker = on)))
}
