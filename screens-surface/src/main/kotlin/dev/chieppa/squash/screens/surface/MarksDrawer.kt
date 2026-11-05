package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedMark
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.screens.sheet.INK_SLOT
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.lines_amount
import dev.chieppa.squash.uidesign.generated.lines_amount_value
import dev.chieppa.squash.uidesign.generated.lines_stroke_color
import dev.chieppa.squash.uidesign.generated.marks_delete
import dev.chieppa.squash.uidesign.generated.marks_fur
import dev.chieppa.squash.uidesign.generated.marks_mirror
import dev.chieppa.squash.uidesign.generated.marks_surface_strokes
import dev.chieppa.squash.uidesign.generated.marks_weight
import dev.chieppa.squash.uidesign.generated.marks_weights
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/** Test tags for the Marks drawer's rows. */
object MarksDrawerTags {
    const val STROKE_COLOR = "marks.stroke.color"
    const val STROKE_MIRROR = "marks.stroke.mirror"
    const val STROKE_DELETE = "marks.stroke.delete"
    const val WEIGHT_AMOUNT = "marks.weight.amount"

    fun weight(id: LineWeightId) = "marks.weight.${id.value}"
    fun stroke(id: MarkId) = "marks.stroke.${id.value}"
    fun strokeSlot(slot: String) = "marks.stroke.slot.$slot"
    fun fur(part: PartId) = "marks.fur.${part.value}"
}

/** A part's row of a list in the Marks drawer: its name and how many it holds. */
data class MarkCount(val part: PartId, val name: String, val count: Int)

/** A stroke's row: the part holding it and what it is called there. */
data class StrokeRow(val part: PartId, val partName: String, val mark: Mark, val name: String)

/** A line weight's row: the part it lies on, the area's name it lies in or else the part's, its id and what it adds. */
data class WeightRow(val part: PartId, val name: String, val id: LineWeightId, val amount: Float)

/** What the Marks drawer lists of [character], a mirrored pair counting once, as the Parts tree reads it. */
class MarkRows(val weights: List<WeightRow>, val strokes: List<StrokeRow>, val fur: List<MarkCount>)

/** The Marks drawer's rows for [character]: the marks outside any group, one row for each stroke and one for each part's fur, and every line weight. */
fun markRows(character: Character, templates: TemplateFolder, library: TemplateLibrary): MarkRows {
    val weights = ArrayList<WeightRow>()
    val strokes = ArrayList<StrokeRow>()
    val fur = ArrayList<MarkCount>()
    for (part in character.parts.filter { it.isLeader }) {
        val surface = character.resolved(part).surface
        val name = partName(part, templates)
        val grouped = surface.groups.flatMap { group -> group.roles.flatMap { it.marks } }.toSet()
        val (furred, plain) = surface.marks.filter { it.id !in grouped }.partition { it.kind == MarkKind.FUR }
        plain.mapTo(strokes) { StrokeRow(part.id, name, it, displayName(it.id.value)) }
        if (furred.isNotEmpty()) fur += MarkCount(part.id, name, furred.size)
        surface.lineWeights.mapTo(weights) { weight ->
            val area = weight.points.getOrNull(weight.points.size / 2)?.let { areaAt(character, library, it) }
            WeightRow(part.id, area?.let(::displayName) ?: name, weight.id, weight.amount)
        }
    }
    return MarkRows(weights, strokes, fur)
}

/**
 * The Marks drawer: every stroke on the character, which picking shows on the model and opens below the list for its color, its mirror and deleting it;
 * every line weight, with its Amount once picked; and each part's fur, whose row selects the part and opens its fur rule in [onOpenLines].
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
        if (rows.strokes.isNotEmpty()) StrokesSection(state, open, character, rows.strokes)
        if (rows.weights.isNotEmpty()) WeightsSection(state, open, rows.weights)
        if (rows.fur.isNotEmpty()) {
            DrawerSection(stringResource(Res.string.marks_fur), divider = false) {
                rows.fur.forEach { row ->
                    ListRow(selected == row.part, { state.selectPart(row.part); onOpenLines() }, Modifier.testTag(MarksDrawerTags.fur(row.part))) {
                        RowIcon(SquashIcons.FurStroke)
                        RowText(row.name, Modifier.weight(1f))
                        RowText(row.count.toString(), subtle = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun StrokesSection(state: AppState, session: CharacterSession, character: Character, strokes: List<StrokeRow>) {
    val lines = state.lines
    DrawerSection(stringResource(Res.string.marks_surface_strokes)) {
        strokes.forEach { row ->
            val picked = PickedMark(row.part, row.mark.id)
            ListRow(lines.pickedMark == picked, { lines.pickedMark = picked.takeUnless { lines.pickedMark == it } }, Modifier.testTag(MarksDrawerTags.stroke(row.mark.id))) {
                RowIcon(SquashIcons.SurfaceStroke)
                RowText(row.name, Modifier.weight(1f))
                RowText(row.partName, subtle = true)
            }
        }
        strokes.firstOrNull { PickedMark(it.part, it.mark.id) == lines.pickedMark }?.let { PickedStroke(state, session, character, it) }
    }
}

/** What can change of one picked stroke. */
@Composable
private fun PickedStroke(state: AppState, session: CharacterSession, character: Character, row: StrokeRow) {
    val part = character.part(row.part) ?: return
    val thisSide = state.thisSide && part.pair != null
    fun edited(change: (Mark) -> Mark) {
        val base = character.editBase(part, thisSide).surface.marks.firstOrNull { it.id == row.mark.id } ?: row.mark
        session.send(SetMark(row.part, row.mark.id, change(base), thisSide))
    }
    val color = stringResource(Res.string.lines_stroke_color)
    SettingRow(color) {
        Spacer(Modifier.weight(1f))
        SlotChoice(character.palette, row.mark.slot ?: INK_SLOT, color, MarksDrawerTags.STROKE_COLOR, { MarksDrawerTags.strokeSlot(it.value) }, { slot ->
            edited { it.copy(slot = slot.takeUnless { chosen -> chosen == INK_SLOT }) }
        })
    }
    if (part.pair == null && part.side == null) {
        val mirror = stringResource(Res.string.marks_mirror)
        SettingRow(mirror) {
            Spacer(Modifier.weight(1f))
            Toggle(row.mark.mirrored, { on -> edited { it.copy(mirrored = on) } }, mirror, Modifier.testTag(MarksDrawerTags.STROKE_MIRROR))
        }
    }
    Button(
        stringResource(Res.string.marks_delete),
        {
            session.send(SetMark(row.part, row.mark.id, null, thisSide))
            state.lines.pickedMark = null
        },
        Modifier.fillMaxWidth().testTag(MarksDrawerTags.STROKE_DELETE),
        kind = ButtonKind.Quiet,
        icon = SquashIcons.RemoveSlot,
    )
}

@Composable
private fun WeightsSection(state: AppState, session: CharacterSession, weights: List<WeightRow>) {
    val lines = state.lines
    val drag = rememberCommandDrag(session)
    val amount = stringResource(Res.string.lines_amount)
    DrawerSection(stringResource(Res.string.marks_weights)) {
        weights.forEach { row ->
            val picked = PickedWeight(row.part, row.id)
            ListRow(lines.picked == picked, { lines.picked = picked; lines.amount = row.amount }, Modifier.testTag(MarksDrawerTags.weight(row.id))) {
                RowIcon(SquashIcons.Lines)
                RowText(stringResource(Res.string.marks_weight, row.name), Modifier.weight(1f))
                RowText(stringResource(Res.string.lines_amount_value, (row.amount * PERCENT).roundToInt()), subtle = true)
            }
            if (lines.picked == picked) {
                SettingRow(amount) {
                    Slider(
                        lines.amount,
                        { value ->
                            lines.amount = value
                            amountCommand(session.document.value.character, picked, value, state.thisSide)?.let(drag::update)
                        },
                        amount,
                        Modifier.weight(1f).testTag(MarksDrawerTags.WEIGHT_AMOUNT),
                        onValueChangeFinished = drag::finish,
                    )
                }
            }
        }
    }
}
