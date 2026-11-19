package dev.chieppa.squash.schemagen.reference

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.positionAt
import kotlin.math.abs

/** The step of u and v a head's chart is read across, either side of its eye landmark. */
private const val STEP = 0.01f

/** How nearly along a head's axis, as a cosine, its facing may lie for its eyes' chart to run along its u. */
private const val ACROSS_AXIS = 0.5f

/** Where an eye sits on a head whose template names no eye landmark. */
private const val DEFAULT_U = 0.6f
private const val DEFAULT_V = 0.2f

/**
 * How far one unit of an eye look's lengths spans in body units on each head template built undrawn at size 1, at its eye landmark:
 * across (toward the face's front) and down, and the irisRatio that makes a round iris there. On an upright head across runs round
 * its v and down along its u; on one resting forward the other way round, as the painted eye's chart reads them.
 */
internal object EyeUnits {
    fun rows(templates: List<PartTemplate>): List<List<String>> = templates
        .filter { it.role == Role.HEAD }
        .groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
        .sortedBy { it.id.value }
        .mapNotNull(::row)

    private fun row(template: PartTemplate): List<String>? {
        val body = PartBuilder.build(template).firstOrNull { it.name == BODY_PATCH } ?: return null
        val eye = template.blocks.flatMap { it.landmarks() }.firstOrNull { it.name == "eye" }
        val u = eye?.u ?: DEFAULT_U
        val v = eye?.v ?: DEFAULT_V
        val alongU = (body.positionAt(u + STEP, v) - body.positionAt(u - STEP, v)).length / (2f * STEP)
        val roundV = (body.positionAt(u, v + STEP) - body.positionAt(u, v - STEP)).length / (2f * STEP)
        val faces = template.axis.faces?.toVec3()?.normalized()
        val upright = faces != null && abs(faces dot template.axis.rest.toVec3().normalized()) <= ACROSS_AXIS
        val across = if (upright) roundV else alongU
        val down = if (upright) alongU else roundV
        val chart = if (upright) "upright: across is v, down is u" else "resting forward: across is u, down is v"
        return listOf(code(template.id.value), chart, num(across), num(down), num(across / down))
    }
}
