package dev.chieppa.squash.screens.shell

import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.core.KeyChord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

private const val FIELD = '\t'
private const val CTRL = 'c'
private const val SHIFT = 's'
private const val ALT = 'a'
private const val META = 'm'

/**
 * Keeps the keymap's changes between runs in [file]: a line per chord, the action's id, its modifiers as letters and its key,
 * split by tabs. An action whose keys were all taken off has a line with its id alone.
 */
class KeymapStore(private val file: Path) {
    fun load(): Map<ActionId, List<KeyChord>> {
        if (!file.exists()) return emptyMap()
        val changes = linkedMapOf<ActionId, MutableList<KeyChord>>()
        for (line in file.readLines()) {
            if (line.isBlank()) continue
            val fields = line.split(FIELD)
            val chords = changes.getOrPut(ActionId(fields[0])) { mutableListOf() }
            if (fields.size == 3 && fields[2].isNotEmpty()) chords += chordOf(fields[1], fields[2])
        }
        return changes
    }

    fun save(changes: Map<ActionId, List<KeyChord>>) {
        file.parent?.let { Files.createDirectories(it) }
        file.writeLines(
            changes.flatMap { (id, chords) ->
                if (chords.isEmpty()) listOf(id.value) else chords.map { listOf(id.value, modifiersOf(it), it.key).joinToString(FIELD.toString()) }
            },
        )
    }

    private fun modifiersOf(chord: KeyChord): String = buildString {
        if (chord.ctrl) append(CTRL)
        if (chord.shift) append(SHIFT)
        if (chord.alt) append(ALT)
        if (chord.meta) append(META)
    }

    private fun chordOf(modifiers: String, key: String) =
        KeyChord(key, ctrl = CTRL in modifiers, shift = SHIFT in modifiers, alt = ALT in modifiers, meta = META in modifiers)
}
