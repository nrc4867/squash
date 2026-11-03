package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_drawer_curves
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_part
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
import dev.chieppa.stretch.frame.DrawerSpec
import dev.chieppa.stretch.frame.FrameLayout
import dev.chieppa.stretch.frame.HistoryPanel
import dev.chieppa.stretch.frame.KeymapEditor
import dev.chieppa.stretch.frame.StretchApp
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.stringResource

private val PARTS_KEYS = listOf("B")
private val TYPES_KEYS = listOf("T")

/** The Build frame every screen but Home sits in: top bar, rails, drawers and status bar around [viewport]. */
@Composable
fun BuildFrame(shell: Shell, modifier: Modifier = Modifier, viewport: @Composable BoxScope.() -> Unit) {
    val state = shell.state
    val screen by state.screen.collectAsState()
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
    Screen.NEW_CHARACTER -> layout.close(DrawerIds.TYPES).close(DrawerIds.PARTS).close(DrawerIds.PART)
    else -> layout.close(DrawerIds.TYPES)
}

@Composable
private fun buildDrawers(shell: Shell, screen: Screen): List<DrawerSpec> {
    val state = shell.state
    val session by state.session.collectAsState()
    val picked by state.pickedType.collectAsState()
    val open = session
    val pickedName = picked.takeIf { screen == Screen.PICK_PART }?.let { state.templates.template(it) }?.name
    val curvesTitle = stringResource(Res.string.edit_drawer_curves).takeIf { rememberPartIsDrawn(state) }
    val parts = DrawerSpec(DrawerIds.PARTS, stringResource(Res.string.shell_drawer_parts), SquashIcons.Parts, keys = PARTS_KEYS) {
        PartsDrawerContent(state)
    }
    val part = DrawerSpec(DrawerIds.PART, pickedName ?: curvesTitle ?: stringResource(Res.string.shell_drawer_part), SquashIcons.Part) {
        PartDrawerContent(state)
    }
    val help = DrawerSpec(DrawerIds.HELP, stringResource(Res.string.shell_drawer_help), StretchIcons.Help) {
        KeymapEditor(shell.keymap, shell.registry.all.toList())
    }
    if (open == null) return listOf(parts, help, part)
    val labels = historyLabels()
    val types = DrawerSpec(DrawerIds.TYPES, stringResource(Res.string.shell_drawer_types), SquashIcons.PartTypes, keys = TYPES_KEYS) {
        PartTypesDrawerContent(state, it)
    }
    val history = DrawerSpec(DrawerIds.HISTORY, stringResource(Res.string.shell_drawer_history), StretchIcons.Clock) {
        HistoryPanel(open.history, labels)
    }
    return listOf(parts, history, types, help, part)
}
