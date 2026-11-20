package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.leaderOf
import dev.chieppa.squash.character.dress.newAreaCommand
import dev.chieppa.squash.character.dress.removeAreaCommand
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.character.dress.shapeCommands
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.OpeningCut
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.RenameGarmentArea
import dev.chieppa.squash.document.SetGarmentAreaColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.dress.library.RenameField
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_area_color
import dev.chieppa.squash.uidesign.generated.dress_area_garment_color
import dev.chieppa.squash.uidesign.generated.dress_area_name
import dev.chieppa.squash.uidesign.generated.dress_area_painting
import dev.chieppa.squash.uidesign.generated.dress_area_remove
import dev.chieppa.squash.uidesign.generated.dress_cuff
import dev.chieppa.squash.uidesign.generated.dress_edge_on
import dev.chieppa.squash.uidesign.generated.dress_hole_close
import dev.chieppa.squash.uidesign.generated.dress_hole_cuff
import dev.chieppa.squash.uidesign.generated.dress_hole_on
import dev.chieppa.squash.uidesign.generated.dress_new_area
import dev.chieppa.squash.uidesign.generated.dress_opening_round
import dev.chieppa.squash.uidesign.generated.dress_opening_v
import dev.chieppa.squash.uidesign.generated.dress_seam
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Toggle
import org.jetbrains.compose.resources.stringResource

private val ICON = 12.dp
private val EDGE_INDENT = 12.dp

/** What joins the names of the parts an area covers. */
private const val NAME_JOIN = ", "

/**
 * A row for each area painted for [garment], picked for Paint by a press and named by a press on it once picked, reading painting while
 * Paint lays strokes in it, else the parts it covers, its color's swatch, and its cross taking it away; under each, a row for each of its edges, a press
 * picking it for Edit curves, a seam read so; then a row for each hole, with Round and V, which cut a neckline's opening round it, its
 * cuff switch and its cross closing it over; then New area.
 */
@Composable
internal fun AreaRows(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val body by remember(session) { session.assembly.output }.collectAsState()
    val held = body ?: return
    val pickedArea by state.dress.area.collectAsState()
    val pickedEdge by state.dress.edge.collectAsState()
    val tool by state.tool.collectAsState()
    var renaming by remember { mutableStateOf<GarmentAreaId?>(null) }
    val painted = remember(character, held, garment) { paintedOf(character, held, garment) }
    val current = painted.area(pickedArea)?.id ?: painted.areas.firstOrNull()?.id
    painted.areas.forEachIndexed { i, area ->
        val name = area.name.ifEmpty { stringResource(Res.string.dress_area_name, i + 1) }
        val picked = area.id == current
        ListRow(picked, {
            if (picked && pickedArea == area.id) renaming = area.id else state.dress.area.value = area.id
        }, Modifier.testTag(DressTags.area(area.id))) {
            Icon(SquashIcons.Area, Stretch.colors.inkMuted, size = ICON)
            if (renaming == area.id) {
                RenameField(name, new = false, contentDescription = name, onKeep = { typed ->
                    renaming = null
                    if (typed.isNotEmpty() && typed != area.name) sendSaved(session, character, held, garment, RenameGarmentArea(garment.id, area.id, typed))
                }, onDrop = { renaming = null }, modifier = Modifier.weight(1f))
            } else {
                RowText(name, Modifier.weight(1f))
                val note = if (picked && tool == BuildTool.PAINT) stringResource(Res.string.dress_area_painting) else partsOf(state, character, area.mask.parts)
                RowText(note, subtle = true)
            }
            AreaColor(session, character, held, garment, area.id, area.color, name)
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_area_remove, name), {
                if (state.dress.area.value == area.id) state.dress.area.value = null
                state.dress.edge.value = null
                session.send(character.removeAreaCommand(held, garment, area.id))
            }, Modifier.testTag(DressTags.removeArea(area.id)))
        }
        painted.edges.forEachIndexed { e, on ->
            if (on.area != i) return@forEachIndexed
            val part = character.part(on.edge.part)?.let(character::leaderOf)
            val label = stringResource(Res.string.dress_edge_on, part?.let { dressPartName(state, it) }.orEmpty())
            ListRow(pickedEdge == e, { state.dress.edge.value = e }, Modifier.padding(start = EDGE_INDENT).testTag(DressTags.edge(e))) {
                Icon(SquashIcons.Edge, Stretch.colors.inkMuted, size = ICON)
                RowText(label, Modifier.weight(1f))
                if (on.edge.seam) RowText(stringResource(Res.string.dress_seam), subtle = true)
            }
        }
    }
    for (hole in painted.holes) HoleRow(state, session, character, held, garment, hole.part, hole.cuff)
    ListRow(false, {
        val (command, id) = character.newAreaCommand(held, garment)
        session.send(command)
        state.dress.area.value = id
    }, Modifier.testTag(DressTags.NEW_AREA)) {
        Icon(SquashIcons.NewArea, Stretch.colors.inkMuted, size = ICON)
        RowText(stringResource(Res.string.dress_new_area), Modifier.weight(1f))
    }
}

@Composable
private fun HoleRow(state: AppState, session: CharacterSession, character: Character, body: AssembledCharacter, garment: Garment, part: PartId, cuff: Boolean) {
    val instance = character.part(part)
    val label = stringResource(Res.string.dress_hole_on, instance?.let { dressPartName(state, it) }.orEmpty())
    ListRow(false, {}, Modifier.testTag(DressTags.hole(part))) {
        Icon(SquashIcons.Hole, Stretch.colors.inkMuted, size = ICON)
        RowText(label, Modifier.weight(1f))
        if (cuff) RowText(stringResource(Res.string.dress_cuff), subtle = true)
        IconButton(SquashIcons.OpeningRound, stringResource(Res.string.dress_opening_round, label), {
            character.openingCommand(body, garment, part, OpeningCut.ROUND)?.let(session::send)
        }, Modifier.testTag(DressTags.opening(part, "round")))
        IconButton(SquashIcons.OpeningV, stringResource(Res.string.dress_opening_v, label), {
            character.openingCommand(body, garment, part, OpeningCut.V)?.let(session::send)
        }, Modifier.testTag(DressTags.opening(part, "v")))
        Toggle(cuff, { on -> character.holeCuffCommand(body, garment, part, on)?.let(session::send) }, stringResource(Res.string.dress_hole_cuff, label), Modifier.testTag(DressTags.holeCuff(part)))
        IconButton(StretchIcons.Close, stringResource(Res.string.dress_hole_close, label), {
            character.closeHoleCommand(body, garment, part)?.let(session::send)
        }, Modifier.testTag(DressTags.closeHole(part)))
    }
}

/**
 * The area [area]'s color as a swatch, its own [color] or the garment's, which opens the palette to pick another, with the garment's
 * color to go back to; the cloth read for a garment never painted as its areas is saved first, so the area colored is its own.
 */
@Composable
private fun AreaColor(session: CharacterSession, character: Character, body: AssembledCharacter, garment: Garment, area: GarmentAreaId, color: SlotName?, name: String) {
    val title = stringResource(Res.string.dress_area_color, name)
    fun send(slot: SlotName?) = sendSaved(session, character, body, garment, SetGarmentAreaColor(garment.id, area, slot))
    SlotChoice(
        character.palette,
        color ?: garment.color,
        title,
        DressTags.areaColor(area),
        { DressTags.areaColor(area) + "." + it.value },
        { send(it) },
    ) { close ->
        if (color != null) {
            Button(stringResource(Res.string.dress_area_garment_color), {
                send(null)
                close()
            }, Modifier.testTag(DressTags.areaGarmentColor(area)), kind = ButtonKind.Quiet)
        }
    }
}

/** The names of [parts], leaders of pairs, joined. */
@Composable
private fun partsOf(state: AppState, character: Character, parts: Set<PartId>): String {
    val names = ArrayList<String>()
    for (part in parts) character.part(part)?.let { names += dressPartName(state, it) }
    return names.joinToString(NAME_JOIN)
}

/** Sends [change] to one of [garment]'s areas, saving the cloth read for a garment never painted as its areas first, so the area changed is its own. */
private fun sendSaved(session: CharacterSession, character: Character, body: AssembledCharacter, garment: Garment, change: DocumentCommand) {
    if (garment.areas.isEmpty()) {
        session.send(Together(listOf(shapeCommands(character, body, garment, paintedOf(character, body, garment)), change)))
    } else {
        session.send(change)
    }
}
