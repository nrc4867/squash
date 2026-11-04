package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.OutlineCurvesSections
import dev.chieppa.squash.screens.edit.SmoothSections
import dev.chieppa.squash.screens.shell.PartDrawerKind
import dev.chieppa.squash.screens.spine.SpineSections

/** The Part drawer's sections for the kinds the drawing tools own. */
@Composable
fun ToolDrawerSections(kind: PartDrawerKind, state: AppState, session: CharacterSession, part: PartInstance, outlines: Map<View, OutlineCurve>) {
    when (kind) {
        PartDrawerKind.OUTLINE_CURVES -> {
            StrokeSection(state)
            OutlineCurvesSections(state, session, part, outlines)
            HintSection(state)
        }
        PartDrawerKind.OUTLINE -> OutlineSections(state)
        PartDrawerKind.SPINE_WIDTH -> SpineSections(state, outlines)
        PartDrawerKind.SMOOTH -> SmoothSections(state)
        PartDrawerKind.PART, PartDrawerKind.MOVE -> Unit
    }
}
