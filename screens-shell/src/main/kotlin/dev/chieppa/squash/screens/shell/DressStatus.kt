package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.holeCount
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.screens.dress.partsUnder
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_no_slot
import dev.chieppa.squash.uidesign.generated.dress_status_bare
import dev.chieppa.squash.uidesign.generated.dress_status_body
import dev.chieppa.squash.uidesign.generated.dress_status_garment
import dev.chieppa.squash.uidesign.generated.dress_status_outfit
import dev.chieppa.squash.uidesign.generated.dress_status_tool
import dev.chieppa.stretch.frame.StatusItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import dev.chieppa.squash.uidesign.generated.dress_count_area
import dev.chieppa.squash.uidesign.generated.dress_count_areas
import dev.chieppa.squash.uidesign.generated.dress_count_edge
import dev.chieppa.squash.uidesign.generated.dress_count_edges
import dev.chieppa.squash.uidesign.generated.dress_count_hole
import dev.chieppa.squash.uidesign.generated.dress_count_holes

/** What joins the names of the parts a garment covers in the status bar, and its counts. */
private const val NAME_JOIN = ", "
private const val COUNT_JOIN = " · "

/** [count] of something, read with [one] for one and [many] otherwise. */
@Composable
private fun counted(count: Int, one: StringResource, many: StringResource): String = stringResource(if (count == 1) one else many, count)

/** The Dress stage's start of the status bar: the stage and its armed tool, Dress · Paint. */
@Composable
internal fun DressStatusTool(tool: BuildTool) {
    val label = toolLabel(tool)?.takeIf { tool.dresses } ?: return
    Box(Modifier.testTag(ShellTags.STATUS_STAGE)) { StatusItem(stringResource(Res.string.dress_status_tool, stringResource(label))) }
}

/**
 * The Dress stage's end of the status bar, by what the armed tool works on: the picked garment's slot, areas, edges and holes while a
 * garment tool other than Edit curves is armed; the body under it, what it covers and its holes, with Edit curves; the shown outfit's
 * filled slots and layers with Loose end or no tool, or with no garment picked.
 */
@Composable
internal fun DressStatusSummary(state: AppState, session: CharacterSession, character: Character, tool: BuildTool) {
    val picked by state.dress.garment.collectAsState()
    val assembled by remember(session) { session.assembly.output }.collectAsState()
    val wardrobe = character.wardrobe
    val garment = picked?.let(wardrobe::garment)
    val text = when {
        garment != null && tool == BuildTool.EDIT_GARMENT -> {
            val under = character.partsUnder(garment, assembled).filter { it.covered }.joinToString(NAME_JOIN) { partName(it.part, state.templates) }
            stringResource(Res.string.dress_status_body, garment.name, under, counted(character.holeCount(garment, assembled), Res.string.dress_count_hole, Res.string.dress_count_holes))
        }
        garment != null && tool.dresses && tool != BuildTool.LOOSE_END -> {
            val slot = garment.slot?.let(wardrobe::slot)?.name ?: stringResource(Res.string.dress_no_slot)
            val painted = assembled?.let { remember(character, it, garment) { paintedOf(character, it, garment) } }
            val counts = listOfNotNull(
                counted(painted?.areas?.size ?: garment.areas.size, Res.string.dress_count_area, Res.string.dress_count_areas),
                counted(painted?.edges?.size ?: 0, Res.string.dress_count_edge, Res.string.dress_count_edges),
                garment.holes.size.takeIf { it > 0 }?.let { counted(it, Res.string.dress_count_hole, Res.string.dress_count_holes) },
            )
            stringResource(Res.string.dress_status_garment, garment.name, slot, counts.joinToString(COUNT_JOIN))
        }
        else -> {
            val outfit = wardrobe.shown?.let(wardrobe::outfit)
            if (outfit == null) {
                stringResource(Res.string.dress_status_bare, wardrobe.slots.size)
            } else {
                stringResource(Res.string.dress_status_outfit, outfit.name, outfit.picks.size, wardrobe.slots.size, wardrobe.worn.size)
            }
        }
    }
    StatusItem(text)
}
