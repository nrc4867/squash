package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.ToolDrawerSections
import dev.chieppa.squash.screens.references.PlaceImageDrawer
import dev.chieppa.squash.screens.references.ReferencesDrawer
import dev.chieppa.squash.screens.references.TraceDrawer
import dev.chieppa.squash.screens.surface.FaceInspector
import dev.chieppa.squash.screens.surface.LinesInspector
import dev.chieppa.squash.screens.surface.paint.ColorDrawerContent
import androidx.compose.runtime.getValue

/** The inspector for [stage]: the armed tool's options first, then what the stage works on. */
@Composable
fun StageInspector(state: AppState, session: CharacterSession, stage: Stage) {
    when (stage) {
        Stage.SHAPE -> ShapeInspector(state) { kind, shown, open, part, outlines -> ToolDrawerSections(kind, shown, open, part, outlines) }
        Stage.COLOR -> ColorDrawerContent(state, session)
        Stage.LINES -> LinesInspector(state)
        Stage.FACE -> FaceInspector(state, session)
    }
}

/** The References drawer: the board of images, the trace each view holds, and the placing of the one being placed. */
@Composable
fun ReferenceDrawers(state: AppState) {
    val tool by state.tool.collectAsState()
    ReferencesDrawer(state)
    TraceDrawer(state)
    if (tool.places) PlaceImageDrawer(state)
}
