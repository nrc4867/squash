package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.new_body_plan
import dev.chieppa.squash.uidesign.generated.new_chip_count
import dev.chieppa.squash.uidesign.generated.new_create
import dev.chieppa.squash.uidesign.generated.new_name
import dev.chieppa.squash.uidesign.generated.new_open_file
import dev.chieppa.squash.uidesign.generated.new_plan_parts
import dev.chieppa.squash.uidesign.generated.new_recent
import dev.chieppa.squash.uidesign.generated.new_title
import dev.chieppa.squash.uidesign.generated.shell_default_name
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.StretchDialog
import dev.chieppa.stretch.frame.Viewport
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.TextField
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 640.dp
private const val LEFT_SHARE = 1.4f

/** New character over an empty viewport: a name, the body plans and the recent files. */
@Composable
fun NewCharacterScreen(shell: Shell) {
    val state = shell.state
    val plans = state.templates.plans
    var name by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(plans.firstOrNull()) }
    val untitled = stringResource(Res.string.shell_default_name)
    BuildFrame(shell) { Viewport {} }
    StretchDialog(
        title = stringResource(Res.string.new_title),
        onDismiss = shell::dismissNew,
        width = DIALOG_WIDTH,
        actions = {
            Button(stringResource(Res.string.new_open_file), shell::chooseAndOpen, Modifier.testTag(ShellTags.NEW_OPEN), icon = StretchIcons.Folder)
            Button(
                stringResource(Res.string.new_create),
                { chosen?.let { shell.create(it, name.trim().ifEmpty { untitled }) } },
                Modifier.testTag(ShellTags.NEW_CREATE),
                kind = ButtonKind.Primary,
            )
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp).testTag(ShellTags.NEW_DIALOG),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(LEFT_SHARE), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Labeled(stringResource(Res.string.new_name)) {
                    TextField(
                        name,
                        { name = it },
                        Modifier.fillMaxWidth().testTag(ShellTags.NEW_NAME),
                        placeholder = untitled,
                        contentDescription = stringResource(Res.string.new_name),
                    )
                }
                Labeled(stringResource(Res.string.new_body_plan)) {
                    plans.forEach { PlanRow(shell, it, it == chosen) { chosen = it } }
                }
                chosen?.let { PlanChips(shell, it) }
            }
            val recent = state.recent.entries
            if (recent.isNotEmpty()) {
                Column(Modifier.weight(1f)) {
                    Labeled(stringResource(Res.string.new_recent)) {
                        for (path in recent) {
                            ListRow(false, { shell.open(path) }, Modifier.testTag(ShellTags.recent(FileStorage.displayName(path)))) {
                                RowIcon(StretchIcons.File)
                                RowText(FileStorage.displayName(path), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label.uppercase(), style = Stretch.type.eyebrow.merge(TextStyle(color = Stretch.colors.inkMuted)))
        content()
    }
}

@Composable
private fun PlanRow(shell: Shell, plan: BodyPlan, selected: Boolean, onClick: () -> Unit) {
    ListRow(selected, onClick, Modifier.testTag(ShellTags.newPlan(plan.id.value))) {
        RowIcon(SquashIcons.Parts)
        RowText(plan.name, Modifier.weight(1f))
        RowText(stringResource(Res.string.new_plan_parts, plan.partCount(shell.state.templates)), subtle = true)
    }
}

@Composable
private fun PlanChips(shell: Shell, plan: BodyPlan) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (chip in plan.chips(shell.state.templates)) {
            val text = if (chip.count > 1) stringResource(Res.string.new_chip_count, chip.name, chip.count) else chip.name
            Chip(text, mono = true)
        }
    }
}
