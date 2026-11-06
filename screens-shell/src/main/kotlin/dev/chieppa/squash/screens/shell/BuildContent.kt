package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState

/** The Part drawer's sections for a [PartDrawerKind] an armed tool owns, over what an edit of [part] starts from in [outlines]. */
typealias ToolSections = @Composable (kind: PartDrawerKind, state: AppState, session: CharacterSession, part: PartInstance, outlines: Map<View, OutlineCurve>) -> Unit

/**
 * What the Build frame holds from the screens beside the shell: the workspace in its viewport, the tools' Part drawer sections, the Color
 * drawer, the Lines and Marks drawers, the Expressions and Angle versions drawers, and the References, Trace and Place image drawers.
 * [marksDrawer] takes what opens the Lines drawer.
 */
class BuildContent(
    val workspace: @Composable (Shell) -> Unit,
    val toolSections: ToolSections,
    val colorDrawer: @Composable (AppState, CharacterSession) -> Unit = { _, _ -> },
    val linesDrawer: @Composable (AppState) -> Unit = {},
    val marksDrawer: @Composable (AppState, onOpenLines: () -> Unit) -> Unit = { _, _ -> },
    val expressionsDrawer: @Composable (AppState) -> Unit = {},
    val expressionActions: @Composable RowScope.(AppState) -> Unit = {},
    val angleVersionsDrawer: @Composable (AppState) -> Unit = {},
    val referencesDrawer: @Composable (AppState) -> Unit = {},
    val traceDrawer: @Composable (AppState) -> Unit = {},
    val placeImageDrawer: @Composable (AppState) -> Unit = {},
)
