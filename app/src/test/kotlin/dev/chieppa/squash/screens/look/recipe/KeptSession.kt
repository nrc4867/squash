package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.build.RecipeBuild
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps

/** A session open on a recipe's character, its pose node and the work it runs on. */
class OpenSession(val session: CharacterSession, val pose: SessionPose, val work: TestWork)

/**
 * One session kept from build to build of a recipe. Each build takes the session back to the empty character it started from and sends
 * the new build's commands, so the graph's caches keep what an edit left as it was and only the rest is derived again. A build that
 * starts from another empty character, or whose commands do not give the same document, gets a session of its own. Only this keeper's
 * own work is ever closed, so sessions kept for other builds in the same process run on.
 */
class KeptSession(private val library: TemplateLibrary = CloseUps.shippedLibrary()) : AutoCloseable {
    private var kept: OpenSession? = null

    /** A session showing [built]'s document. */
    fun open(built: RecipeBuild): OpenSession {
        val empty = emptyOf(built.document.character)
        kept?.let { open ->
            val history = open.session.history
            history.jumpTo(0)
            if (history.document.value == empty) {
                built.commands.forEach(open.session::send)
                if (open.session.document.value == built.document) return open
            }
        }
        close()
        val work = TestWork()
        val session = CharacterSession(empty, library, work.scope, work.dispatcher)
        built.commands.forEach(session::send)
        if (session.document.value != built.document) {
            work.close()
            return fresh(built.document)
        }
        return OpenSession(session, SessionPose(session), work).also { kept = it }
    }

    /** A session over [document] alone, kept until the next build. */
    private fun fresh(document: SquashDocument): OpenSession {
        val work = TestWork()
        val session = CharacterSession(document, library, work.scope, work.dispatcher)
        return OpenSession(session, SessionPose(session), work).also { kept = it }
    }

    override fun close() {
        kept?.work?.close()
        kept = null
    }

    private fun emptyOf(character: Character) = SquashDocument(Character(character.id, character.name, character.bodyPlan))
}
