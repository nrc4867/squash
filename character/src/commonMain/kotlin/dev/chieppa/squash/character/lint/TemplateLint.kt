package dev.chieppa.squash.character.lint

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.sectionProfile
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.ParamSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.character.model.TEMPLATE_FORMAT
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3

private const val MIN_RINGS_PER_JOINT = 2
private const val MIN_SEGMENTS = 3

/** How close to 1 the cosine of a rest axis's angle from straight down comes for it to count as straight down. */
private const val STRAIGHT_DOWN = 0.9999f
private const val PATCH_SEPARATOR = ":"
private val AXIS_READINGS = setOf(Sets.HEIGHT, Sets.WIDTH)
private val SECTION_READINGS = setOf(Sets.CAP, Sets.SECTION)

/** The semantic checks a part template's schema cannot express. See HLD: data-model, Verifiable. */
object TemplateLint {
    fun lint(template: PartTemplate): List<Finding> =
        format(template) + params(template) + axis(template) + section(template) + views(template) + openEnd(template) + mount(template) +
            blocks(template) + joints(template) + names(template) + patchNames(template) + grain(template) + generic(template)

    /**
     * A generic part's choices keep the template's limits: its topology inside them, enough of the fewest rings for its joints, a
     * named section to start from, and each view setting what it sets when a part turns it on.
     */
    private fun generic(template: PartTemplate): List<Finding> {
        val generic = template.generic ?: return emptyList()
        return buildList {
            if (generic.rings.min > generic.rings.max) add(Finding("$.generic.rings", "the fewest rings are above the most"))
            if (generic.segments.min > generic.segments.max) add(Finding("$.generic.segments", "the fewest segments are above the most"))
            if (generic.segments.min < MIN_SEGMENTS) add(Finding("$.generic.segments", "a section needs at least $MIN_SEGMENTS control points"))
            if (template.topology.rings !in generic.rings) add(Finding("$.topology.rings", "${template.topology.rings} rings are outside the generic limits"))
            if (template.topology.segments !in generic.segments) add(Finding("$.topology.segments", "${template.topology.segments} segments are outside the generic limits"))
            val needed = template.blocks.sumOf { it.joints().size } * MIN_RINGS_PER_JOINT
            if (generic.rings.min < needed) add(Finding("$.generic.rings", "${generic.rings.min} rings for its joints needs at least $needed"))
            if (template.sectionProfile() == null) add(Finding("$.section", "a generic template starts from a named section"))
            template.views.forEach { (view, sets) ->
                if (GenericParams.READINGS[view] != sets) add(Finding("$.views.${view.name.lowercase()}", "a generic part's ${view.name.lowercase()} sets ${GenericParams.READINGS[view]?.name?.lowercase()}"))
            }
        }
    }

    private fun grain(template: PartTemplate): List<Finding> = template.grain.ruffs.flatMapIndexed { i, ruff ->
        val context = LintContext(template, "$.grain.ruffs[$i]")
        context.unit("u", ruff.u) + context.unit("v", ruff.v)
    }

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
            spec.values?.let { addAll(values(path, spec, it)) }
        }
    }

    private fun values(path: String, spec: ParamSpec, values: List<Float>): List<Finding> = buildList {
        if (spec.default.isFlag) add(Finding(path, "a flag takes no values"))
        if (spec.min != null || spec.max != null) add(Finding(path, "values set the limits, so min and max are left out"))
        if (values.isEmpty()) add(Finding(path, "values are empty"))
        if (values.zipWithNext().any { (a, b) -> a >= b }) add(Finding(path, "values must ascend"))
        if (!spec.default.isFlag && spec.default.value !in values) add(Finding(path, "default ${spec.default.value} is not one of the values"))
    }

    private fun axis(template: PartTemplate): List<Finding> {
        val context = LintContext(template, "$.axis")
        return context.param("bend", template.axis.bend) + context.unit("bendAt", template.axis.bendAt) + bearing(context, template)
    }

    /** Move's angles keep an axis off the ground only from straight down, so a leg's squareness never reaches 0. */
    private fun bearing(context: LintContext, template: PartTemplate): List<Finding> {
        val down = template.axis.rest.toVec3().normalized() dot Vec3.Y
        return if (template.axis.bearsWeight && down > -STRAIGHT_DOWN) context.finding("bearsWeight", "an axis that bears weight rests straight down") else emptyList()
    }

    private fun section(template: PartTemplate) = LintContext(template, "$").section("section", template.section)

    /** Each reading has one view, and Front alone looks along the axis, so it sets a section or cap and nothing else does. */
    private fun views(template: PartTemplate): List<Finding> = buildList {
        val views = template.views
        views.entries.groupBy { it.value }.filter { it.value.size > 1 }.values.forEach { same ->
            add(Finding("$.views", "${same.size} views set ${same.first().value.name.lowercase()}"))
        }
        if (views.values.none { it in AXIS_READINGS }) add(Finding("$.views", "no view sets height or width"))
        if (views.values.containsAll(SECTION_READINGS)) add(Finding("$.views", "cap and section are both set"))
        views.forEach { (view, sets) ->
            val reads = (view == View.FRONT) == (sets in SECTION_READINGS)
            if (!reads) add(Finding("$.views.${view.name.lowercase()}", "${view.name.lowercase()} cannot set ${sets.name.lowercase()}"))
        }
    }

    /** The open end's ring sits on the socket plane, so exactly one run ends there. */
    private fun openEnd(template: PartTemplate): List<Finding> {
        val end = template.openEnd ?: return emptyList()
        val at = if (end == End.START) 0f else 1f
        val count = template.blocks.count { it.reaches(at) }
        return if (count == 1) emptyList() else listOf(Finding("$.openEnd", "$count runs reach the open end"))
    }

    /** A mount carries a closed part; an open end already says where the part sits. */
    private fun mount(template: PartTemplate): List<Finding> {
        val mount = template.mount ?: return emptyList()
        val context = LintContext(template, "$.mount")
        val closed = if (template.openEnd == null) emptyList() else context.finding("u", "a part with an open end has no mount")
        return closed + context.unit("u", mount.u) + context.unit("v", mount.v) + context.onTheSurface("u", mount.u, null)
    }

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

    /** Anchors name patches, so no two blocks may build one of the same name. */
    private fun patchNames(template: PartTemplate): List<Finding> {
        val names = template.blocks.flatMap { it.patchNames() }
        val same = names.groupBy { it.name }.filter { it.value.size > 1 }.keys
        val inRow = names.filter { !it.repeats }.filter { fixed ->
            names.any { row -> row.repeats && fixed.name.startsWith(row.name + PATCH_SEPARATOR) }
        }
        return (same + inRow.map(PatchName::name)).distinct().map { Finding("$.blocks", "two patches named '$it'") }
    }
}
