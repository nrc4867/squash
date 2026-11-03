package dev.chieppa.squash.character.lint

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.TEMPLATE_FORMAT

private const val MIN_RINGS_PER_JOINT = 2

/** The semantic checks a part template's schema cannot express. See HLD: data-model, Verifiable. */
object TemplateLint {
    fun lint(template: PartTemplate): List<Finding> =
        format(template) + params(template) + section(template) + blocks(template) + joints(template) + names(template)

    private fun format(template: PartTemplate) =
        if (template.format == TEMPLATE_FORMAT) emptyList()
        else listOf(Finding("$.format", "format ${template.format} is not $TEMPLATE_FORMAT"))

    private fun params(template: PartTemplate): List<Finding> = template.params.flatMap { (name, spec) ->
        val path = "$.params.$name"
        val value = spec.default.value
        buildList {
            if (spec.min != null && spec.max != null && spec.min > spec.max) add(Finding(path, "minimum is above maximum"))
            if (spec.min != null && value < spec.min) add(Finding(path, "default $value is below the minimum"))
            if (spec.max != null && value > spec.max) add(Finding(path, "default $value is above the maximum"))
        }
    }

    private fun section(template: PartTemplate) = LintContext(template, "$").section("section", template.section)

    private fun blocks(template: PartTemplate): List<Finding> = template.blocks.flatMapIndexed { i, block ->
        val context = LintContext(template, "$.blocks[$i]")
        context.flag("if", block.condition) + block.lint(context)
    }

    private fun joints(template: PartTemplate): List<Finding> {
        val joints = template.blocks.flatMap { it.joints() }
        val outOfOrder = joints.zipWithNext().filter { (a, b) -> a.u > b.u }.map { (a, b) ->
            Finding("$.blocks", "joint '${b.name}' comes before '${a.name}' along the axis")
        }
        val needed = joints.size * MIN_RINGS_PER_JOINT
        val rings = template.topology.rings
        val enough = if (rings >= needed) emptyList() else {
            listOf(Finding("$.topology.rings", "$rings rings for ${joints.size} joints needs at least $needed"))
        }
        return outOfOrder + enough
    }

    /** References are by name, so a name means one thing within its kind. */
    private fun names(template: PartTemplate): List<Finding> {
        val groups = listOf(
            "joint" to template.blocks.flatMap { it.joints() }.map { it.name },
            "socket" to template.blocks.flatMap { it.sockets() }.map { it.name },
            "area" to template.blocks.flatMap { it.areas() }.map { it.name },
        )
        return groups.flatMap { (kind, names) ->
            names.groupBy { it }.filter { it.value.size > 1 }.keys.map { Finding("$.blocks", "two ${kind}s named '$it'") }
        }
    }
}
