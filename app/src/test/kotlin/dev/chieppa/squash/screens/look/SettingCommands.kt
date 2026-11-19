package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.isMouth

/** The mark group controls `set=` reaches, by name, each with how it writes a value. */
private val CONTROLS: Map<String, (MarkControls, Float) -> MarkControls> = mapOf(
    "open" to { c, v -> c.copy(open = v.coerceIn(0f, 1f)) },
    "width" to { c, v -> c.copy(width = v) },
    "expressionBlend" to { c, v -> c.copy(expressionBlend = v.coerceIn(0f, 1f)) },
)

/**
 * The command `set=<part>:<name>:<value>` stands for on [session]'s character: a template parameter or a dotted part parameter by
 * [name]; `<group>.<control>` a mark group's control (open, width, expressionBlend); or a bare control name the part's one mouth's.
 * Null with a warning through [warn] when the part or the name is not there, naming what is.
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
    val reachable = params.sorted() + groups.flatMap { group -> CONTROLS.keys.map { "${group.id.value}.$it" } }
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
