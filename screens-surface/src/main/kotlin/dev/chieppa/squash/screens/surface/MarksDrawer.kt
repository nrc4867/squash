package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.MarkKind
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.lines_amount_value
import dev.chieppa.squash.uidesign.generated.lines_interior
import dev.chieppa.squash.uidesign.generated.lines_silhouette
import dev.chieppa.squash.uidesign.generated.marks_auto
import dev.chieppa.squash.uidesign.generated.marks_fur
import dev.chieppa.squash.uidesign.generated.marks_lines
import dev.chieppa.squash.uidesign.generated.marks_pixels
import dev.chieppa.squash.uidesign.generated.marks_surface_strokes
import dev.chieppa.squash.uidesign.generated.marks_weight
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/** Test tags for the Marks drawer's rows. */
object MarksDrawerTags {
    const val SILHOUETTE = "marks.silhouette"
    const val INTERIOR = "marks.interior"

    fun weight(id: LineWeightId) = "marks.weight.${id.value}"
    fun strokes(part: PartId) = "marks.strokes.${part.value}"
    fun fur(part: PartId) = "marks.fur.${part.value}"
}

/** A part's row of a list in the Marks drawer: its name and how many it holds. */
data class MarkCount(val part: PartId, val name: String, val count: Int)

/** A line weight's row: the part it lies on, the area's name it lies in or else the part's, its id and what it adds. */
data class WeightRow(val part: PartId, val name: String, val id: LineWeightId, val amount: Float)

/** What the Marks drawer lists of [character], a mirrored pair counting once, as the Parts tree reads it. */
class MarkRows(val weights: List<WeightRow>, val strokes: List<MarkCount>, val fur: List<MarkCount>)

/** The Marks drawer's rows for [character]: the marks outside any group, fur apart from the rest, and every line weight. */
fun markRows(character: Character, templates: TemplateFolder, library: TemplateLibrary): MarkRows {
    val weights = ArrayList<WeightRow>()
    val strokes = ArrayList<MarkCount>()
    val fur = ArrayList<MarkCount>()
    for (part in character.parts.filter { it.isLeader }) {
        val surface = character.resolved(part).surface
        val name = partName(part, templates)
        val grouped = surface.groups.flatMap { group -> group.roles.flatMap { it.marks } }.toSet()
        val (furred, plain) = surface.marks.filter { it.id !in grouped }.partition { it.kind == MarkKind.FUR }
        if (plain.isNotEmpty()) strokes += MarkCount(part.id, name, plain.size)
        if (furred.isNotEmpty()) fur += MarkCount(part.id, name, furred.size)
        surface.lineWeights.mapTo(weights) { weight ->
            val area = weight.points.getOrNull(weight.points.size / 2)?.let { areaAt(character, library, it) }
            WeightRow(part.id, area?.let(::displayName) ?: name, weight.id, weight.amount)
        }
    }
    return MarkRows(weights, strokes, fur)
}

/**
 * The Marks drawer: under Lines the rules, reading auto until they are changed, and each line weight; then the surface strokes and
 * fur on each part with their counts. A rule's row opens the Lines drawer, a weight's picks it for Amount, and a part's selects it.
 */
@Composable
fun MarksDrawer(state: AppState, onOpenLines: () -> Unit) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val character = document.character
    val rows = markRows(character, state.templates, open.templates)
    val selected by state.selectedPart.collectAsState()
    Column {
        DrawerSection(stringResource(Res.string.marks_lines)) {
            RuleRow(stringResource(Res.string.lines_silhouette), character.lineRules.silhouette, LineRules.DEFAULT_SILHOUETTE, MarksDrawerTags.SILHOUETTE, onOpenLines)
            RuleRow(stringResource(Res.string.lines_interior), character.lineRules.interior, LineRules.DEFAULT_INTERIOR, MarksDrawerTags.INTERIOR, onOpenLines)
            rows.weights.forEach { row ->
                val picked = PickedWeight(row.part, row.id)
                ListRow(state.lines.picked == picked, { state.lines.picked = picked; state.lines.amount = row.amount }, Modifier.testTag(MarksDrawerTags.weight(row.id))) {
                    RowIcon(SquashIcons.Lines)
                    RowText(stringResource(Res.string.marks_weight, row.name), Modifier.weight(1f))
                    RowText(stringResource(Res.string.lines_amount_value, (row.amount * PERCENT).roundToInt()), subtle = true)
                }
            }
        }
        DrawerSection(stringResource(Res.string.marks_surface_strokes)) {
            rows.strokes.forEach { CountRow(it, SquashIcons.SurfaceStroke, MarksDrawerTags.strokes(it.part), selected == it.part) { state.selectPart(it.part) } }
        }
        DrawerSection(stringResource(Res.string.marks_fur), divider = false) {
            rows.fur.forEach { CountRow(it, SquashIcons.FurStroke, MarksDrawerTags.fur(it.part), selected == it.part) { state.selectPart(it.part) } }
        }
    }
}

@Composable
private fun RuleRow(label: String, pixels: Float, default: Float, tag: String, onOpen: () -> Unit) {
    ListRow(false, onOpen, Modifier.testTag(tag)) {
        RowIcon(SquashIcons.Lines)
        RowText(label, Modifier.weight(1f))
        RowText(if (pixels == default) stringResource(Res.string.marks_auto) else stringResource(Res.string.marks_pixels, weightLabel(pixels)), subtle = true)
    }
}

@Composable
private fun CountRow(row: MarkCount, icon: ImageVector, tag: String, selected: Boolean, onClick: () -> Unit) {
    ListRow(selected, onClick, Modifier.testTag(tag)) {
        RowIcon(icon)
        RowText(row.name, Modifier.weight(1f))
        RowText(row.count.toString(), subtle = true)
    }
}
