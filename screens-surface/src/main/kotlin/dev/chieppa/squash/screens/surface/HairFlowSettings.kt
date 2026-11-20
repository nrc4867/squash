package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.MOST_COWLICKS
import dev.chieppa.squash.document.MOST_FLOW_AROUND
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartingKind
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.SetScalpFlow
import dev.chieppa.squash.document.scalpFlowOn
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_hair_cowlick_add
import dev.chieppa.squash.uidesign.generated.face_hair_cowlick_remove
import dev.chieppa.squash.uidesign.generated.face_hair_flow
import dev.chieppa.squash.uidesign.generated.face_hair_part_at
import dev.chieppa.squash.uidesign.generated.face_hair_part_centre
import dev.chieppa.squash.uidesign.generated.face_hair_part_none
import dev.chieppa.squash.uidesign.generated.face_hair_part_side
import dev.chieppa.squash.uidesign.generated.face_hair_sweep
import dev.chieppa.squash.uidesign.generated.face_hair_whorl_around
import dev.chieppa.squash.uidesign.generated.face_hair_whorl_turn
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Radio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where a side parting a parting of none or the centre turns into sits, -1 to 1 across the top. */
private const val SIDE_PART_AT = 0.6f

private val PART_NAMES: Map<PartingKind, StringResource> = mapOf(
    PartingKind.NONE to Res.string.face_hair_part_none,
    PartingKind.CENTRE to Res.string.face_hair_part_centre,
    PartingKind.SIDE to Res.string.face_hair_part_side,
)

/** Test tags for the Hair section's Flow. */
object HairFlowTags {
    const val ADD_COWLICK = "face.hair.flow.cowlick.add"

    fun part(kind: PartingKind) = "face.hair.flow.part.${kind.name.lowercase()}"
    fun setting(name: String) = "face.hair.flow.$name"
    fun removeCowlick(index: Int) = "face.hair.flow.cowlick.$index.remove"
}

/**
 * The Hair section's Flow, the scalp flow over [head]: the parting's kind and where across the top it runs, the whorl's place round the
 * head and its turn, the overall sweep, and cowlicks added and removed; each choice and each drag one undo step.
 */
@Composable
internal fun FlowSettings(session: CharacterSession, head: PartId) {
    val document by session.document.collectAsState()
    val flow = document.character.scalpFlowOn(head)
    fun send(change: ScalpFlow) = session.send(SetScalpFlow(head, change))
    Divider(label = stringResource(Res.string.face_hair_flow))
    for (kind in PartingKind.entries) {
        Radio(
            flow.parting.kind == kind,
            {
                val at = if (kind == PartingKind.SIDE && flow.parting.at == 0f) SIDE_PART_AT else if (kind == PartingKind.CENTRE) 0f else flow.parting.at
                send(flow.copy(parting = flow.parting.copy(kind = kind, at = at)))
            },
            stringResource(PART_NAMES.getValue(kind)),
            Modifier.testTag(HairFlowTags.part(kind)),
        )
    }
    if (flow.parting.shown) {
        FlowSlider(session, head, "part.at", stringResource(Res.string.face_hair_part_at), flow.parting.at, -1f..1f) { f, v -> f.copy(parting = f.parting.copy(at = v)) }
    }
    FlowSlider(session, head, "whorl.around", stringResource(Res.string.face_hair_whorl_around), flow.whorl.around, -MOST_FLOW_AROUND..MOST_FLOW_AROUND) { f, v -> f.copy(whorl = f.whorl.copy(around = v)) }
    FlowSlider(session, head, "whorl.turn", stringResource(Res.string.face_hair_whorl_turn), flow.whorl.turn, -1f..1f) { f, v -> f.copy(whorl = f.whorl.copy(turn = v)) }
    FlowSlider(session, head, "sweep", stringResource(Res.string.face_hair_sweep), flow.sweep, -1f..1f) { f, v -> f.copy(sweep = v) }
    flow.cowlicks.forEachIndexed { index, _ ->
        key(index) {
            Box(Modifier.testTag(HairFlowTags.removeCowlick(index))) {
                Button(stringResource(Res.string.face_hair_cowlick_remove), { send(flow.copy(cowlicks = flow.cowlicks.filterIndexed { i, _ -> i != index })) }, kind = ButtonKind.Quiet)
            }
        }
    }
    Button(
        stringResource(Res.string.face_hair_cowlick_add),
        { send(flow.copy(cowlicks = flow.cowlicks + Cowlick())) },
        Modifier.testTag(HairFlowTags.ADD_COWLICK),
        kind = ButtonKind.Quiet,
        enabled = flow.cowlicks.size < MOST_COWLICKS,
    )
}

/** One of the flow's numbers on a slider; one drag is one undo step, the values it passes through previews of a single [SetScalpFlow]. */
@Composable
private fun FlowSlider(
    session: CharacterSession,
    head: PartId,
    name: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (ScalpFlow, Float) -> ScalpFlow,
) = HairSlider(session, HairFlowTags.setting(name), label, value, range) { document, next ->
    SetScalpFlow(head, change(document.character.scalpFlowOn(head), next))
}
