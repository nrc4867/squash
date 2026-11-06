package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.template
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_drawer_color
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_lines
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_references
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
import dev.chieppa.stretch.frame.DrawerSpec
import dev.chieppa.stretch.frame.FrameLayout
import dev.chieppa.stretch.frame.HistoryPanel
import dev.chieppa.stretch.frame.KeymapEditor
import dev.chieppa.stretch.frame.StretchApp
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.chieppa.squash.uidesign.generated.shell_drawer_inspector
import dev.chieppa.squash.uidesign.generated.stage_face
import dev.chieppa.squash.uidesign.generated.stage_shape
import androidx.compose.runtime.getValue

/** The Build frame every screen but Home sits in: top bar, rails, drawers and status bar around [viewport]. */
@Composable
fun BuildFrame(shell: Shell, screen: Screen, modifier: Modifier = Modifier, viewport: @Composable BoxScope.() -> Unit) {
    val shown = shownLayout(shell.layout, screen)
    StretchApp(
        layout = shown,
        onLayoutChange = { shell.changeLayout(shown, it) },
        drawers = buildDrawers(shell, screen),
        onResetLayout = { shell.layout = DEFAULT_LAYOUT },
        modifier = modifier,
        statusBar = { BuildStatusBar(shell) },
        topBar = { ShellTopBar(shell) },
        viewport = viewport,
    )
}

/** What [layout] shows on [screen]: Part types is open only on Pick, and New character has every drawer shut. */
private fun shownLayout(layout: FrameLayout, screen: Screen): FrameLayout = when (screen) {
    Screen.PICK_PART -> layout.open(DrawerIds.TYPES)
    Screen.NEW_CHARACTER -> layout.close(DrawerIds.TYPES).close(DrawerIds.PARTS).close(DrawerIds.INSPECTOR)
    else -> layout.close(DrawerIds.TYPES)
}

/** A stage's name, which titles the inspector and the stage switcher. */
val Stage.label: StringResource
    get() = when (this) {
        Stage.SHAPE -> Res.string.stage_shape
        Stage.COLOR -> Res.string.shell_drawer_color
        Stage.LINES -> Res.string.shell_drawer_lines
        Stage.FACE -> Res.string.stage_face
    }

@Composable
private fun buildDrawers(shell: Shell, screen: Screen): List<DrawerSpec> {
    val state = shell.state
    val session by state.session.collectAsState()
    val picked by state.pickedType.collectAsState()
    val stage by state.stage.collectAsState()
    val keys = LocalActionKeys.current
    val open = session
    val pickedName = picked.takeIf { screen == Screen.PICK_PART }?.let { state.templates.template(it) }?.name
    val parts = DrawerSpec(DrawerIds.PARTS, stringResource(Res.string.shell_drawer_parts), SquashIcons.Parts, keys = keys(ActionKeys.PARTS)) {
        PartsDrawerContent(state)
    }
    val inspector = DrawerSpec(
        DrawerIds.INSPECTOR,
        pickedName ?: stringResource(stage.label),
        SquashIcons.Part,
        railLabel = stringResource(Res.string.shell_drawer_inspector),
    ) {
        if (open == null || screen != Screen.WORKSPACE) PickedTypeOrNone(state) else shell.content.inspector(this, state, open, stage)
    }
    val help = DrawerSpec(DrawerIds.HELP, stringResource(Res.string.shell_drawer_help), StretchIcons.Help) {
        KeymapEditor(shell.keymap, shell.registry.all.toList(), onChange = shell::keymapChanged)
    }
    if (open == null) return listOf(parts, help, inspector)
    val labels = historyLabels()
    val types = DrawerSpec(DrawerIds.TYPES, stringResource(Res.string.shell_drawer_types), SquashIcons.PartTypes, keys = keys(ActionKeys.TYPES)) {
        PartTypesDrawerContent(state, it)
    }
    val history = DrawerSpec(DrawerIds.HISTORY, stringResource(Res.string.shell_drawer_history), StretchIcons.Clock) {
        HistoryPanel(open.history, labels)
    }
    return listOf(
        parts,
        types,
        DrawerSpec(DrawerIds.REFERENCES, stringResource(Res.string.shell_drawer_references), SquashIcons.References) { shell.content.referencesDrawer(state) },
        history,
        help,
        inspector,
    )
}
