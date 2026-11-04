package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.uidesign.generated.smooth_tool
import dev.chieppa.squash.uidesign.generated.move_section
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.draw_drawer_outline
import dev.chieppa.squash.uidesign.generated.edit_drawer_curves
import dev.chieppa.squash.uidesign.generated.shell_drawer_color
import dev.chieppa.squash.uidesign.generated.shell_drawer_help
import dev.chieppa.squash.uidesign.generated.shell_drawer_history
import dev.chieppa.squash.uidesign.generated.shell_drawer_lines
import dev.chieppa.squash.uidesign.generated.shell_drawer_marks
import dev.chieppa.squash.uidesign.generated.shell_drawer_part
import dev.chieppa.squash.uidesign.generated.shell_drawer_parts
import dev.chieppa.squash.uidesign.generated.shell_drawer_references
import dev.chieppa.squash.uidesign.generated.shell_drawer_rig
import dev.chieppa.squash.uidesign.generated.shell_drawer_slots
import dev.chieppa.squash.uidesign.generated.shell_drawer_types
import dev.chieppa.squash.uidesign.generated.shell_part_template
import dev.chieppa.squash.uidesign.generated.spine_tool
import dev.chieppa.stretch.frame.DrawerSpec
import dev.chieppa.stretch.frame.FrameLayout
import dev.chieppa.stretch.frame.HistoryPanel
import dev.chieppa.stretch.frame.KeymapEditor
import dev.chieppa.stretch.frame.StretchApp
import dev.chieppa.stretch.theme.StretchIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PARTS_KEYS = listOf("B")
private val TYPES_KEYS = listOf("T")

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
    Screen.NEW_CHARACTER -> layout.close(DrawerIds.TYPES).close(DrawerIds.PARTS).close(DrawerIds.PART)
    else -> layout.close(DrawerIds.TYPES)
}

@Composable
private fun rememberPartDrawerKind(state: AppState): PartDrawerKind {
    val tool by state.tool.collectAsState()
    val selected by state.selectedPart.collectAsState()
    return partDrawerKind(tool, selected != null, rememberPartIsDrawn(state))
}

/** The selected part's template, whose name titles its drawer. */
@Composable
private fun rememberSelectedTemplate(state: AppState): PartTemplate? {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value
    return selected?.let { document?.character?.part(it) }?.let { state.templates.template(it.template) }
}

@Composable
private fun partDrawerTitle(state: AppState, kind: PartDrawerKind): String {
    val template = rememberSelectedTemplate(state)
    return when (kind) {
        PartDrawerKind.PART -> if (template != null) stringResource(Res.string.shell_part_template, template.name) else stringResource(Res.string.shell_drawer_part)
        PartDrawerKind.OUTLINE -> stringResource(Res.string.draw_drawer_outline)
        PartDrawerKind.OUTLINE_CURVES -> stringResource(Res.string.edit_drawer_curves)
        PartDrawerKind.SPINE_WIDTH -> stringResource(Res.string.spine_tool)
        PartDrawerKind.SMOOTH -> stringResource(Res.string.smooth_tool)
        PartDrawerKind.MOVE -> stringResource(Res.string.move_section)
    }
}

/** A drawer the boards draw on the rail whose own board is not built: it holds its place and does not open. */
@Composable
private fun comingDrawer(id: String, title: StringResource, icon: ImageVector) =
    DrawerSpec(id, stringResource(title), icon, enabled = false) {}

@Composable
private fun buildDrawers(shell: Shell, screen: Screen): List<DrawerSpec> {
    val state = shell.state
    val session by state.session.collectAsState()
    val picked by state.pickedType.collectAsState()
    val open = session
    val pickedName = picked.takeIf { screen == Screen.PICK_PART }?.let { state.templates.template(it) }?.name
    val kind = rememberPartDrawerKind(state)
    val partTitle = partDrawerTitle(state, kind)
    val partHeader = when {
        pickedName != null -> SquashIcons.Part
        kind == PartDrawerKind.OUTLINE -> SquashIcons.DrawOutline
        kind == PartDrawerKind.OUTLINE_CURVES -> SquashIcons.EditCurves
        kind == PartDrawerKind.SPINE_WIDTH -> SquashIcons.SpineAndWidth
        else -> SquashIcons.Part
    }
    val parts = DrawerSpec(DrawerIds.PARTS, stringResource(Res.string.shell_drawer_parts), SquashIcons.Parts, keys = PARTS_KEYS) {
        PartsDrawerContent(state)
    }
    val partLabel = stringResource(Res.string.shell_drawer_part)
    val part = DrawerSpec(DrawerIds.PART, pickedName ?: partTitle, SquashIcons.Part, headerIcon = partHeader, railLabel = partLabel) {
        PartDrawerContent(state, shell.content.toolSections)
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
    return listOf(
        parts,
        types,
        comingDrawer(DrawerIds.REFERENCES, Res.string.shell_drawer_references, SquashIcons.References),
        history,
        help,
        part,
        comingDrawer(DrawerIds.RIG, Res.string.shell_drawer_rig, SquashIcons.Rig),
        comingDrawer(DrawerIds.SLOTS, Res.string.shell_drawer_slots, SquashIcons.Slots),
        comingDrawer(DrawerIds.COLOR, Res.string.shell_drawer_color, SquashIcons.Color),
        comingDrawer(DrawerIds.LINES, Res.string.shell_drawer_lines, SquashIcons.Lines),
        comingDrawer(DrawerIds.MARKS, Res.string.shell_drawer_marks, SquashIcons.Marks),
    )
}
