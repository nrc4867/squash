package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.role.PartRoles
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role

/** The roles of [character]'s parts, over the loaded templates and its plan. */
fun TemplateFolder.roles(character: Character): PartRoles = PartRoles(character, { template(it) }, plan(character.bodyPlan))

/** What [part] most likely is from where it sits on [character]; see [PartRoles.guess]. */
fun guessRole(character: Character, part: PartInstance, templates: TemplateFolder): Role = templates.roles(character).guess(part)
