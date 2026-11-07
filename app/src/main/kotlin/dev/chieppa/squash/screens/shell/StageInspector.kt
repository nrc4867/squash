package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.ToolDrawerSections
import dev.chieppa.squash.screens.references.ReferencesDrawer
import dev.chieppa.squash.screens.references.TraceDrawer
import dev.chieppa.squash.screens.surface.FaceInspector
import dev.chieppa.squash.screens.surface.LinesInspector
import dev.chieppa.squash.screens.surface.rig.RigInspector
import dev.chieppa.squash.screens.surface.paint.ColorDrawerContent

/** The inspector for [stage]: the armed tool's options first, then what the stage works on. */
@Composable
fun StageInspector(state: AppState, session: CharacterSession, stage: Stage) {
    when (stage) {
        Stage.SHAPE -> ShapeInspector(state) { kind, shown, open, part, outlines -> ToolDrawerSections(kind, shown, open, part, outlines) }
        Stage.COLOR -> ColorDrawerContent(state, session)
        Stage.LINES -> LinesInspector(state)
        Stage.FACE -> FaceInspector(state, session)
        Stage.RIG -> RigInspector(state, session)
    }
}

/** The References drawer: the board of images and the trace each view holds. */
@Composable
fun ReferenceDrawers(state: AppState) {
    ReferencesDrawer(state)
    TraceDrawer(state)
}
