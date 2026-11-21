package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.isMouth

/** The mark group controls `set=` reaches, by name, each with how it writes a value. */
private val CONTROLS: Map<String, (MarkControls, Float) -> MarkControls> = mapOf(
    "open" to { c, v -> c.copy(open = v.coerceIn(0f, 1f)) },
    "wide" to { c, v -> c.copy(wide = v.coerceIn(-1f, 1f)) },
    "round" to { c, v -> c.copy(round = v.coerceIn(0f, 1f)) },
    "smile" to { c, v -> c.copy(smile = v.coerceIn(-1f, 1f)) },
    "upperLip" to { c, v -> c.copy(upperLip = v.coerceIn(0f, 1f)) },
    "lowerLip" to { c, v -> c.copy(lowerLip = v.coerceIn(-1f, 1f)) },
    "tongue" to { c, v -> c.copy(tongue = v.coerceIn(0f, 1f)) },
    "expressionBlend" to { c, v -> c.copy(expressionBlend = v.coerceIn(0f, 1f)) },
)

/** The name `set=` takes for the spoken shape a mouth is shown saying. */
private const val SHAPE = "shape"

/**
 * Applies `set=<part>:<name>:<value>` to [session]: `<group>.shape` (or `shape` for a part's one mouth) shows every mouth saying the shape
 * the value names, as a preview, no edit; anything else is sent as [settingCommand] makes it. Warns through [warn] where nothing applies.
 */
fun applySetting(session: CharacterSession, part: String, name: String, value: String, warn: (String) -> Unit) {
    if (name.substringAfterLast('.') == SHAPE) {
        val instance = session.document.value.character.part(PartId(part)) ?: return warn("set warning: no part \"$part\"")
        val groups = session.document.value.character.resolved(instance).surface.groups
        val group = name.substringBeforeLast('.', "")
        val held = if (group.isEmpty()) groups.singleOrNull { it.isMouth } else groups.firstOrNull { it.id.value == group && it.isMouth }
        if (held == null) return warn("set warning: $part has no mouth \"$group\"")
        val shape = MouthShapeName.of(value) ?: return warn("set warning: no mouth shape \"$value\"; shapes: ${MouthShapeName.entries.joinToString { it.word }}")
        session.mouthPreview.value = shape
        return
    }
    val number = value.toFloatOrNull() ?: return warn("set warning: $name takes a number, not \"$value\"")
    settingCommand(session, part, name, number, warn)?.let(session::send)
}

/**
 * The command `set=<part>:<name>:<value>` stands for on [session]'s character: a template parameter or a dotted part parameter by
 * [name]; `<group>.<control>` a mark group's control (open, wide, round, smile, upperLip, lowerLip, tongue, expressionBlend); or a bare
 * control name the part's one mouth's. Null with a warning through [warn] when the part or the name is not there, naming what is.
 */
fun settingCommand(session: CharacterSession, part: String, name: String, value: Float, warn: (String) -> Unit): DocumentCommand? {
    val character = session.document.value.character
    val instance = character.part(PartId(part)) ?: run {
        warn("set warning: no part \"$part\"; parts: ${character.parts.joinToString { it.id.value }}")
        return null
    }
    val groups = character.resolved(instance).surface.groups
    val control = controlOf(groups, name)
    if (control != null) {
        val (group, write) = control
        return SetMarkControls(instance.id, group.id, write(group.controls, value))
    }
    val params = session.builtTemplate(instance).params.keys
    if (name in params || '.' in name && groups.none { name.startsWith(it.id.value + ".") }) return SetParameter(instance.id, name, value)
    val reachable = params.sorted() + groups.flatMap { group -> (CONTROLS.keys + SHAPE).map { "${group.id.value}.$it" } }
    warn("set warning: $part has no parameter or mark group control \"$name\"; it has ${reachable.joinToString().ifEmpty { "none" }}")
    return null
}

private fun controlOf(groups: List<MarkGroup>, name: String): Pair<MarkGroup, (MarkControls, Float) -> MarkControls>? {
    val group = name.substringBeforeLast('.', "")
    val control = name.substringAfterLast('.')
    val write = CONTROLS[control] ?: return null
    val held = if (group.isEmpty()) groups.singleOrNull { it.isMouth } else groups.firstOrNull { it.id.value == group }
    return held?.let { it to write }
}
