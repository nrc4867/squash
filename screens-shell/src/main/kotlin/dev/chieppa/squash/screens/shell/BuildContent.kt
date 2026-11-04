package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState

/** The Part drawer's sections for a [PartDrawerKind] an armed tool owns, over what an edit of [part] starts from in [outlines]. */
typealias ToolSections = @Composable (kind: PartDrawerKind, state: AppState, session: CharacterSession, part: PartInstance, outlines: Map<View, OutlineCurve>) -> Unit

/** What the Build frame holds from the screens beside the shell: the workspace in its viewport and the tools' Part drawer sections. */
class BuildContent(val workspace: @Composable (Shell) -> Unit, val toolSections: ToolSections)
