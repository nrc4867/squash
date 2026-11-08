package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.chips
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.isUpright
import dev.chieppa.squash.character.catalog.isFinned
import dev.chieppa.squash.character.catalog.isWinged
import dev.chieppa.squash.character.catalog.partCount
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.new_body_plan
import dev.chieppa.squash.uidesign.generated.new_chip_count
import dev.chieppa.squash.uidesign.generated.new_create
import dev.chieppa.squash.uidesign.generated.new_name
import dev.chieppa.squash.uidesign.generated.new_open_file
import dev.chieppa.squash.uidesign.generated.new_plan_blank
import dev.chieppa.squash.uidesign.generated.new_plan_parts
import dev.chieppa.squash.uidesign.generated.new_plan_upright
import dev.chieppa.squash.uidesign.generated.new_plan_finned
import dev.chieppa.squash.uidesign.generated.new_plan_winged
import dev.chieppa.squash.uidesign.generated.new_recent
import dev.chieppa.squash.uidesign.generated.new_title
import dev.chieppa.squash.uidesign.generated.rest_pose_art
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
private val PREVIEW_DP = 150.dp

/** The dialog's preview renders near its own size, so its dashes stay as heavy as the board's. */
private const val PREVIEW_WIDTH = 760
private const val PREVIEW_HEIGHT = 300

/** New character over an empty viewport: a name, the body plans and the recent files. */
@Composable
fun NewCharacterScreen(shell: Shell) {
    val state = shell.state
    val plans = state.templates.plans
    var name by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(plans.firstOrNull()) }
    val untitled = stringResource(Res.string.shell_default_name)
    val backdrop = rememberRestPoseArt(state, chosen, dashed = true)
    val art = rememberRestPoseArt(state, chosen, dashed = true, width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT)
    val description = stringResource(Res.string.rest_pose_art)
    Viewport { if (backdrop != null) RestPoseImage(backdrop, description, Modifier.fillMaxSize()) }
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
                if (art != null) {
                    RestPoseImage(
                        art,
                        description,
                        Modifier.fillMaxWidth().height(PREVIEW_DP).clip(RoundedCornerShape(Stretch.shapes.card)).background(Stretch.colors.surfaceWell).testTag(ShellTags.NEW_ART),
                    )
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

/**
 * A body plan to start from: a species plan with its part count, a winged one, the bird, with a perched bird and what it grows, a finned
 * one, the fish, with a fish and its fins, an upright one, the biped, with a standing figure and what it stands on, or one that places nothing past its root, Blank, read as one tube.
 */
@Composable
private fun PlanRow(shell: Shell, plan: BodyPlan, selected: Boolean, onClick: () -> Unit) {
    val blank = plan.sockets.isEmpty()
    val winged = !blank && plan.isWinged(shell.state.templates)
    val finned = !blank && !winged && plan.isFinned(shell.state.templates)
    val upright = !blank && plan.isUpright()
    ListRow(selected, onClick, Modifier.testTag(ShellTags.newPlan(plan.id.value))) {
        RowIcon(
            when {
                blank -> SquashIcons.PlanBlank
                winged -> SquashIcons.PlanBird
                finned -> SquashIcons.PlanFish
                upright -> SquashIcons.PlanBiped
                else -> SquashIcons.BodyPlan
            },
        )
        RowText(plan.name, Modifier.weight(1f))
        val note = when {
            blank -> stringResource(Res.string.new_plan_blank)
            winged -> stringResource(Res.string.new_plan_winged)
            finned -> stringResource(Res.string.new_plan_finned)
            upright -> stringResource(Res.string.new_plan_upright)
            else -> stringResource(Res.string.new_plan_parts, plan.partCount(shell.state.templates))
        }
        RowText(note, subtle = true)
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
