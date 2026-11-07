package dev.chieppa.squash.character.block

import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.PatchSlot
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.LandmarkSpec
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import kotlinx.serialization.Serializable

/**
 * A building block of a part template, tagged by `kind` in a file. A block owns its behaviour, so a new kind is one
 * class; nothing downstream switches on kind. See HLD: data-model, Adding parts without refactoring.
 */
@Serializable
sealed interface Block {
    val name: String?

    /** The boolean parameter that switches this block on. */
    val condition: ParamRef?

    fun build(ctx: BuildContext): List<Patch> = emptyList()
    fun lint(ctx: LintContext): List<Finding> = emptyList()
    fun joints(): List<JointSpec> = emptyList()

    /** This block with joint [name], if it holds it, at [u]. */
    fun withJointAt(name: String, u: Float): Block = this

    fun sockets(): List<SocketSpec> = emptyList()
    fun landmarks(): List<LandmarkSpec> = emptyList()
    fun areas(): List<AreaSpec> = emptyList()

    /** The patches this block shows in a palette slot of their own. */
    fun patchSlots(): List<PatchSlot> = emptyList()

    /** The stretch of the axis this block's run of slices covers, if it builds one. */
    fun run(): Span? = null

    /** The ring this block's run stands at [u] before its ends close, if its run reaches there. */
    fun ringAt(ctx: BuildContext, u: Float): Slice? = null

    /** The patches this block builds. */
    fun patchNames(): List<PatchName> = emptyList()

    /** The shape this block closes [end] with, if it closes it. */
    fun capShape(end: End): CapShape? = null

    /** Whether the run this block builds is a sheet: open in v, its two faces meeting at an edge. */
    fun isSheet(): Boolean = false
}
