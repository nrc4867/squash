package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.readText

/** The fennec's bones: 37 along its parts and two on each of the three toes of its four paws. */
const val FENNEC_BONES = 37 + 4 * 3 * 2

/** The shipped templates and plans. */
val shippedLibrary: TemplateLibrary by lazy { TemplateFolder.shipped().let { TemplateLibrary(it.parts, it.plans) } }

/** The example fennec, as saved under examples/. */
fun savedFennec(): SquashDocument = readDocument(Path.of(System.getProperty("squash.examples"), "fennec.json").readText())

/** The fennec assembled and rigged by a session's graph once it has settled. */
class RiggedFennec(val assembled: AssembledCharacter, val rig: Rig)

@OptIn(ExperimentalCoroutinesApi::class)
val fennec: RiggedFennec by lazy {
    var rigged: RiggedFennec? = null
    runTest {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        check(session.graph.settled.value) { "The fennec's graph did not settle" }
        rigged = RiggedFennec(checkNotNull(session.assembly.output.value), checkNotNull(node.output.value))
    }
    checkNotNull(rigged)
}
