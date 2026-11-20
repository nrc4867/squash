package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.character.hair.hairPresetCommand
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.LONGEST_LOCK
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.MOST_LOCKS
import dev.chieppa.squash.document.MOST_LOCK_CURL
import dev.chieppa.squash.document.LEAST_LOCKS
import dev.chieppa.squash.document.SHORTEST_LOCK
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.lockGroup
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_hair
import dev.chieppa.squash.uidesign.generated.face_hair_count
import dev.chieppa.squash.uidesign.generated.face_hair_curl
import dev.chieppa.squash.uidesign.generated.face_hair_kind_back
import dev.chieppa.squash.uidesign.generated.face_hair_kind_crown
import dev.chieppa.squash.uidesign.generated.face_hair_kind_fringe
import dev.chieppa.squash.uidesign.generated.face_hair_kind_left
import dev.chieppa.squash.uidesign.generated.face_hair_kind_right
import dev.chieppa.squash.uidesign.generated.face_hair_kind_strays
import dev.chieppa.squash.uidesign.generated.face_hair_kind_top
import dev.chieppa.squash.uidesign.generated.face_hair_length
import dev.chieppa.squash.uidesign.generated.face_hair_preset
import dev.chieppa.squash.uidesign.generated.face_hair_preset_bob
import dev.chieppa.squash.uidesign.generated.face_hair_preset_curly
import dev.chieppa.squash.uidesign.generated.face_hair_preset_long_straight
import dev.chieppa.squash.uidesign.generated.face_hair_preset_ponytail
import dev.chieppa.squash.uidesign.generated.face_hair_preset_short_spiky
import dev.chieppa.squash.uidesign.generated.face_hair_preset_side_swept
import dev.chieppa.squash.uidesign.generated.face_hair_remove
import dev.chieppa.squash.uidesign.generated.face_hair_spread
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Menu
import dev.chieppa.stretch.frame.MenuItem
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Test tags for the Hair section. */
object HairTags {
    const val PRESET = "face.hair.preset"
    const val REMOVE = "face.hair.remove"

    fun preset(preset: HairPreset) = "face.hair.preset.${preset.name.lowercase()}"
    fun group(id: String) = "face.hair.group.$id"
    fun setting(name: String) = "face.hair.$name"
}

private val PRESET_NAMES: Map<HairPreset, StringResource> = mapOf(
    HairPreset.SHORT_SPIKY to Res.string.face_hair_preset_short_spiky,
    HairPreset.BOB to Res.string.face_hair_preset_bob,
    HairPreset.LONG_STRAIGHT to Res.string.face_hair_preset_long_straight,
    HairPreset.PONYTAIL to Res.string.face_hair_preset_ponytail,
    HairPreset.SIDE_SWEPT to Res.string.face_hair_preset_side_swept,
    HairPreset.CURLY to Res.string.face_hair_preset_curly,
)

private val KIND_NAMES: Map<LockGroupKind, StringResource> = mapOf(
    LockGroupKind.FRINGE to Res.string.face_hair_kind_fringe,
    LockGroupKind.LEFT to Res.string.face_hair_kind_left,
    LockGroupKind.RIGHT to Res.string.face_hair_kind_right,
    LockGroupKind.BACK to Res.string.face_hair_kind_back,
    LockGroupKind.CROWN to Res.string.face_hair_kind_crown,
    LockGroupKind.STRAYS to Res.string.face_hair_kind_strays,
    LockGroupKind.TOP to Res.string.face_hair_kind_top,
)

/**
 * The Hair section of the Face inspector, on the face's head: Preset, whose menu gives the head a preset's lock groups in one step, a row
 * a group, and the picked group's count, length, spread and curl, each drag one undo step, and Remove group; then, on a head with
 * locks, its scalp flow ([FlowSettings]).
 */
@Composable
fun HairSection(session: CharacterSession) {
    val document by session.document.collectAsState()
    val assembled by session.assembly.output.collectAsState()
    val character = document.character
    val head = character.faceHead(session.templates) ?: return
    val groups = character.lockGroups.filter { it.on == head }
    var picked by remember(head) { mutableStateOf<LockGroupId?>(null) }
    var open by remember { mutableStateOf(false) }
    DrawerSection(stringResource(Res.string.face_hair)) {
        Box {
            Button(stringResource(Res.string.face_hair_preset), { open = true }, Modifier.fillMaxWidth().testTag(HairTags.PRESET), kind = ButtonKind.Quiet, icon = SquashIcons.HairLocks, enabled = assembled != null)
            Menu(open, { open = false }) {
                for (preset in HairPreset.entries) {
                    Box(Modifier.testTag(HairTags.preset(preset))) {
                        MenuItem(stringResource(PRESET_NAMES.getValue(preset)), {
                            open = false
                            val built = assembled ?: return@MenuItem
                            hairPresetCommand(session.document.value.character, built, head, preset)?.let(session::send)
                            picked = null
                        })
                    }
                }
            }
        }
        for (group in groups) {
            Box(Modifier.testTag(HairTags.group(group.id.value))) {
                Button(stringResource(KIND_NAMES.getValue(group.kind)), { picked = group.id }, kind = if (picked == group.id) ButtonKind.Default else ButtonKind.Quiet, icon = SquashIcons.LockGroup)
            }
        }
        val group = picked?.let(character::lockGroup)
        if (group != null) {
            key(group.id) { GroupSettings(session, group) { picked = null } }
        }
        if (groups.isNotEmpty()) FlowSettings(session, head)
    }
}

/** The picked group's count, length, spread and curl, and Remove group. */
@Composable
private fun GroupSettings(session: CharacterSession, group: LockGroup, removed: () -> Unit) {
    val id = group.id
    GroupSlider(session, id, "count", stringResource(Res.string.face_hair_count), group.count.toFloat(), LEAST_LOCKS.toFloat()..MOST_LOCKS.toFloat()) { g, v -> g.copy(count = v.roundToInt(), overrides = g.overrides.filter { it.index < v.roundToInt() }) }
    GroupSlider(session, id, "length", stringResource(Res.string.face_hair_length), group.length, SHORTEST_LOCK..LONGEST_LOCK.coerceAtMost(group.length * 3f).coerceAtLeast(0.3f)) { g, v -> g.copy(length = v) }
    GroupSlider(session, id, "spread", stringResource(Res.string.face_hair_spread), group.spread, 0f..2f) { g, v -> g.copy(spread = v) }
    GroupSlider(session, id, "curl", stringResource(Res.string.face_hair_curl), group.curl, -MOST_LOCK_CURL..MOST_LOCK_CURL) { g, v -> g.copy(curl = v) }
    Button(stringResource(Res.string.face_hair_remove), {
        session.send(SetLockGroup(id, null))
        removed()
    }, Modifier.testTag(HairTags.REMOVE), kind = ButtonKind.Quiet)
}

/** One of a group's numbers on a slider; one drag is one undo step, the values it passes through previews of a single [SetLockGroup]. */
@Composable
private fun GroupSlider(
    session: CharacterSession,
    id: LockGroupId,
    name: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (LockGroup, Float) -> LockGroup,
) = HairSlider(session, HairTags.setting(name), label, value, range) { document, next ->
    document.character.lockGroup(id)?.let { SetLockGroup(id, change(it, next)) }
}
