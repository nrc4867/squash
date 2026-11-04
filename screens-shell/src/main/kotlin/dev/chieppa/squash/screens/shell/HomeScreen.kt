package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.home_card_detail
import dev.chieppa.squash.uidesign.generated.home_cast_detail
import dev.chieppa.squash.uidesign.generated.home_clear_recent
import dev.chieppa.squash.uidesign.generated.home_continue
import dev.chieppa.squash.uidesign.generated.home_drop_kinds
import dev.chieppa.squash.uidesign.generated.home_drop_over
import dev.chieppa.squash.uidesign.generated.home_drop_reject
import dev.chieppa.squash.uidesign.generated.home_drop_title
import dev.chieppa.squash.uidesign.generated.home_empty
import dev.chieppa.squash.uidesign.generated.home_new
import dev.chieppa.squash.uidesign.generated.home_new_character
import dev.chieppa.squash.uidesign.generated.home_new_project
import dev.chieppa.squash.uidesign.generated.home_open_file
import dev.chieppa.squash.uidesign.generated.home_projects
import dev.chieppa.squash.uidesign.generated.home_recent_files
import dev.chieppa.squash.uidesign.generated.home_search
import dev.chieppa.squash.uidesign.generated.home_show_cast
import dev.chieppa.squash.uidesign.generated.home_show_objects
import dev.chieppa.squash.uidesign.generated.home_show_sets
import dev.chieppa.squash.uidesign.generated.home_stats
import dev.chieppa.squash.uidesign.generated.home_today
import dev.chieppa.squash.uidesign.generated.rest_pose_art
import dev.chieppa.squash.uidesign.generated.shell_status_ready
import dev.chieppa.squash.uidesign.generated.shell_status_saved
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.frame.Drawer
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.DrawerStyle
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.MenuDivider
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.StatusBar
import dev.chieppa.stretch.frame.StatusItem
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.theme.StretchSizes
import dev.chieppa.stretch.ui.AddCard
import dev.chieppa.stretch.ui.ArtCard
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.CardSize
import dev.chieppa.stretch.ui.Dropzone
import dev.chieppa.stretch.ui.EmptyState
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.TextField
import dev.chieppa.stretch.ui.Tooltip
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PROJECTS_WIDTH = 260.dp
private val ART_ICON = 56.dp
private val ADD_CARD_HEIGHT = 120.dp
private const val CARD_SHARE = 1.25f
private const val STAMP = "\u0001"

private val SEARCH_WIDTH = 220.dp
private val CAST_HEIGHT = 220.dp

private enum class HomeShows(val label: StringResource) {
    CAST(Res.string.home_show_cast),
    OBJECTS(Res.string.home_show_objects),
    SETS(Res.string.home_show_sets),
}

/** The paths whose file name holds [query], whatever its case. */
internal fun List<String>.matching(query: String): List<String> =
    filter { query.isBlank() || FileStorage.displayName(it).contains(query.trim(), ignoreCase = true) }

/** Home: the recent files, and the chosen one ready to continue. */
@Composable
fun HomeScreen(shell: Shell) {
    val recent = shell.state.recent.entries
    var chosen by remember { mutableStateOf(recent.firstOrNull()) }
    var search by remember { mutableStateOf("") }
    val current = chosen?.takeIf { it in recent } ?: recent.firstOrNull()
    Column(Modifier.fillMaxSize().background(Stretch.colors.surfaceCanvas).testTag(ShellTags.HOME)) {
        ShellTopBar(shell)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ProjectsDrawer(shell, recent.matching(search), current) { chosen = it }
            Box(Modifier.width(Stretch.stroke).fillMaxHeight().background(Stretch.colors.linePanel))
            HomeMain(shell, current, search, { search = it }, Modifier.weight(1f).fillMaxHeight())
        }
        StatusBar(
            start = {
                if (current != null) {
                    StatusItem(stringResource(Res.string.shell_status_saved), StretchIcons.Check)
                } else {
                    StatusItem(stringResource(Res.string.shell_status_ready))
                }
            },
            end = { if (current != null) StatusItem(FileStorage.displayName(current)) },
        )
    }
}

@Composable
private fun ProjectsDrawer(shell: Shell, recent: List<String>, current: String?, onChoose: (String) -> Unit) {
    Drawer(
        stringResource(Res.string.home_projects),
        StretchIcons.Folder,
        Modifier.width(PROJECTS_WIDTH).fillMaxHeight(),
        style = DrawerStyle.Docked,
        actions = {
            HeaderAction(StretchIcons.Plus, stringResource(Res.string.home_new_project), listOf("Ctrl", "N"), shell::newCharacter)
            HeaderAction(StretchIcons.Folder, stringResource(Res.string.home_open_file), listOf("Ctrl", "O"), shell::chooseAndOpen)
        },
    ) {
        if (recent.isNotEmpty()) DrawerSection(stringResource(Res.string.home_recent_files)) {
            for (path in recent) {
                val name = FileStorage.displayName(path)
                ListRow(path == current, { onChoose(path) }, Modifier.testTag(ShellTags.recent(name))) {
                    RowIcon(StretchIcons.File)
                    RowText(name, Modifier.weight(1f))
                }
            }
            MenuDivider()
            MenuItem(stringResource(Res.string.home_clear_recent), { shell.state.recent.clear() })
        }
        DrawerSection(divider = false) {
            Dropzone(
                label = stringResource(Res.string.home_drop_title),
                kinds = stringResource(Res.string.home_drop_kinds),
                extensions = setOf(shell.state.format.extension),
                onFiles = { files -> files.firstOrNull()?.let(shell::open) },
                onBrowse = shell::chooseAndOpen,
                overLabel = stringResource(Res.string.home_drop_over),
                rejectLabel = stringResource(Res.string.home_drop_reject),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The file's character and New character. A file holds one character and no objects or sets yet, so those show faint. */
@Composable
private fun CastRow(shell: Shell, current: String, project: RecentProject?) {
    val labels = HomeShows.entries.associateWith { stringResource(it.label) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(HomeShows.entries, HomeShows.CAST, {}, { labels.getValue(it) }, enabled = { it == HomeShows.CAST })
        Row(Modifier.fillMaxWidth().height(CAST_HEIGHT), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArtCard(
                project?.name ?: FileStorage.displayName(current),
                Modifier.weight(1f).fillMaxHeight().testTag(ShellTags.HOME_CAST),
                detail = project?.let { stringResource(Res.string.home_cast_detail, it.progress.drawn, it.progress.total) },
                onClick = { shell.open(current) },
            ) {
                val plan = project?.let { shell.state.templates.plan(it.document.character.bodyPlan) }
                val art = rememberRestPoseArt(shell.state, plan, project?.document)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (art != null) RestPoseImage(art, stringResource(Res.string.rest_pose_art), Modifier.fillMaxSize())
                    else Icon(SquashIcons.Character, Stretch.colors.inkMuted, size = ART_ICON)
                }
            }
            AddCard(stringResource(Res.string.home_new_character), shell::newCharacter, Modifier.weight(1f).height(ADD_CARD_HEIGHT))
            Spacer(Modifier.weight(2f))
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, keys: List<String>, onClick: () -> Unit) {
    Tooltip(label, keys) { IconButton(icon, label, onClick, size = StretchSizes.seg) }
}

@Composable
private fun HomeMain(shell: Shell, current: String?, search: String, onSearch: (String) -> Unit, modifier: Modifier) {
    if (current == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                StretchIcons.Folder,
                stringResource(Res.string.home_empty),
                actionLabel = stringResource(Res.string.home_new_character),
                onAction = shell::newCharacter,
            )
        }
        return
    }
    val c = Stretch.colors
    val project by produceState<RecentProject?>(null, current) {
        value = withContext(Dispatchers.IO) { readRecentProject(current, shell.state.format, shell.state.templates) }
    }
    val today = stringResource(Res.string.home_today, STAMP)
    Column(modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    FileStorage.displayName(current),
                    style = Stretch.type.pageTitle.merge(TextStyle(color = c.inkStrong)),
                    maxLines = 1,
                )
                BasicText(stringResource(Res.string.home_stats), style = Stretch.type.value.merge(TextStyle(color = c.inkMuted)), maxLines = 1)
            }
            TextField(
                search,
                onSearch,
                Modifier.width(SEARCH_WIDTH).testTag(ShellTags.HOME_SEARCH),
                placeholder = stringResource(Res.string.home_search),
                icon = StretchIcons.Search,
            )
            Button(stringResource(Res.string.home_new), shell::newCharacter, Modifier.testTag(ShellTags.HOME_NEW), icon = StretchIcons.Plus)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val loaded = project
            val stamp = loaded?.let { recentStamp(it.modified, Instant.now(), ZoneId.systemDefault()) { time -> today.replace(STAMP, time) } }
            ArtCard(
                loaded?.name ?: FileStorage.displayName(current),
                Modifier.weight(CARD_SHARE).fillMaxHeight(),
                detail = if (loaded != null && stamp != null) stringResource(Res.string.home_card_detail, loaded.progress.drawn, loaded.progress.total, stamp) else null,
                size = CardSize.Large,
                trailing = {
                    Button(
                        stringResource(Res.string.home_continue),
                        { shell.open(current) },
                        Modifier.testTag(ShellTags.HOME_CONTINUE),
                        kind = ButtonKind.Primary,
                    )
                },
            ) {
                val plan = loaded?.let { shell.state.templates.plan(it.document.character.bodyPlan) }
                val art = rememberRestPoseArt(shell.state, plan, loaded?.document)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (art != null) RestPoseImage(art, stringResource(Res.string.rest_pose_art), Modifier.fillMaxSize().testTag(ShellTags.HOME_ART))
                    else Icon(SquashIcons.Character, c.inkMuted, size = ART_ICON)
                }
            }
        }
        CastRow(shell, current, project)
    }
}
