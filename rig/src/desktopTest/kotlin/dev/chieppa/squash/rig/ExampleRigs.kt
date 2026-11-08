package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.readDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.readText

/** The example saved under examples/ as [file], rigged by a session's graph once it has settled. */
@OptIn(ExperimentalCoroutinesApi::class)
fun exampleRig(file: String): Rig {
    var rig: Rig? = null
    runTest {
        val document = readDocument(Path.of(System.getProperty("squash.examples"), file).readText())
        val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        check(session.graph.settled.value) { "The graph of $file did not settle" }
        rig = checkNotNull(node.output.value)
    }
    return checkNotNull(rig)
}

/** The example person, rigged once for every test that reads it. */
val personRig: Rig by lazy { exampleRig("person.json") }

/** The example toucan, rigged once for every test that reads it. */
val toucanRig: Rig by lazy { exampleRig("toucan.json") }
