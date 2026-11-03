package dev.chieppa.squash.character.block

import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.geom.Patch
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
    fun sockets(): List<SocketSpec> = emptyList()
    fun areas(): List<AreaSpec> = emptyList()

    /** The shape this block closes [end] with, if it closes it. */
    fun capShape(end: End): CapShape? = null
}
