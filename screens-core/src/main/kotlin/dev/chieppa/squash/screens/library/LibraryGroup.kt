package dev.chieppa.squash.screens.library

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Role
import dev.chieppa.stretch.frame.fuzzySearch

/** The body areas Library groups templates by, in the order it shows them; a template whose role none names is a Shape. */
enum class LibraryGroup(val roles: Set<Role>) {
    BODY(setOf(Role.BODY, Role.SPINE, Role.NECK)),
    HEAD(setOf(Role.HEAD, Role.JAW, Role.EAR, Role.HAIR)),
    LIMBS(setOf(Role.ARM, Role.LEG)),
    HANDS_FEET(setOf(Role.HAND, Role.FOOT)),
    TAIL(setOf(Role.TAIL)),
    WINGS_FINS(setOf(Role.WING, Role.FIN)),
    HORNS_CLAWS(setOf(Role.HORN)),
    SHAPES(setOf(Role.OTHER)),
    ;

    companion object {
        fun of(template: PartTemplate): LibraryGroup = entries.firstOrNull { template.role in it.roles } ?: SHAPES
    }
}

/** One group of Library's Parts half with the templates it shows. */
data class LibraryShelf(val group: LibraryGroup, val templates: List<PartTemplate>)

/**
 * Library's groups over [templates]: with a blank [query], every group that holds one, in [LibraryGroup]'s order with the groups
 * holding a template [first] names moved ahead; with a query, only the templates matching it by name or [roleName], and only groups
 * with a match, each group's best match first.
 */
fun libraryShelves(
    templates: List<PartTemplate>,
    query: String,
    first: (PartTemplate) -> Boolean = { false },
    roleName: (PartTemplate) -> String = { it.role?.name?.lowercase().orEmpty() },
): List<LibraryShelf> {
    val shown = if (query.isBlank()) templates else fuzzySearch(query.trim(), templates) { listOf(it.name, roleName(it)) }
    val shelves = shown.groupBy(LibraryGroup::of).map { (group, held) -> LibraryShelf(group, held) }.sortedBy { it.group.ordinal }
    if (query.isNotBlank()) return shelves
    val (ahead, rest) = shelves.partition { shelf -> shelf.templates.any(first) }
    return ahead + rest
}

/** The template a search puts the cursor on: the best match, or null with a blank query or no match. */
fun bestMatch(templates: List<PartTemplate>, query: String, roleName: (PartTemplate) -> String = { it.role?.name?.lowercase().orEmpty() }): PartTemplate? =
    if (query.isBlank()) null else fuzzySearch(query.trim(), templates) { listOf(it.name, roleName(it)) }.firstOrNull()
