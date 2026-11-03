package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.document.DocumentFormat
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** What Home says about a recent file, read from the file itself. */
data class RecentProject(val path: String, val name: String, val progress: PartProgress, val modified: Instant)

/** Reads the save at [path], or null when it cannot be read as one. */
fun readRecentProject(path: String, format: DocumentFormat<SquashDocument>, templates: TemplateFolder): RecentProject? =
    runCatching {
        val file = Path.of(path)
        val document = format.decode(Files.readAllBytes(file))
        val plan = requireNotNull(templates.plan(document.character.bodyPlan))
        RecentProject(path, document.character.name, partProgress(document, plan, templates), Files.getLastModifiedTime(file).toInstant())
    }.getOrNull()

/** When a file changed: its time through [today] when that was today, otherwise its date. */
fun recentStamp(modified: Instant, now: Instant, zone: ZoneId, today: (String) -> String): String {
    val at = modified.atZone(zone)
    return if (at.toLocalDate() == now.atZone(zone).toLocalDate()) today(TIME.format(at)) else DATE.format(at)
}
