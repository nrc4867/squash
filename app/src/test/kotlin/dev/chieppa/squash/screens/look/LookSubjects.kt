package dev.chieppa.squash.screens.look

import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.dragon.dragonDocument
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.squash.screens.fennec.fennecWingedDocument
import dev.chieppa.squash.screens.koi.koiDocument
import dev.chieppa.squash.screens.lizard.lizardDocument
import dev.chieppa.squash.screens.person.personDocument
import dev.chieppa.squash.screens.person.personDressedDocument
import dev.chieppa.squash.screens.toucan.toucanDocument
import dev.chieppa.squash.screens.twin.TWIN_POSES
import dev.chieppa.squash.screens.twin.twinDocument
import dev.chieppa.squash.screens.twin.twinDressedDocument
import dev.chieppa.squash.screens.woman.WOMAN_POSES
import dev.chieppa.squash.screens.woman.womanDocument
import dev.chieppa.squash.screens.woman.womanDressedDocument
import java.io.File

/** Each example by the name of its file under `examples/`, as its fixture's commands make it. */
private val FIXTURES: Map<String, () -> SquashDocument> = mapOf(
    "dragon" to ::dragonDocument,
    "fennec" to ::fennecDocument,
    "fennec-dressed" to ::fennecDressedDocument,
    "fennec-winged" to ::fennecWingedDocument,
    "koi" to ::koiDocument,
    "lizard" to ::lizardDocument,
    "person" to ::personDocument,
    "person-dressed" to ::personDressedDocument,
    "toucan" to ::toucanDocument,
    "woman" to ::womanDocument,
    "woman-dressed" to ::womanDressedDocument,
    "woman-twin" to ::twinDocument,
    "woman-twin-dressed" to ::twinDressedDocument,
)

/** The poses an example is looked at in besides rest, by name. */
private val POSES: Map<String, Map<String, Pose>> = mapOf(
    "woman" to WOMAN_POSES,
    "woman-dressed" to WOMAN_POSES,
    "woman-twin" to TWIN_POSES,
    "woman-twin-dressed" to TWIN_POSES,
)

/** Every example the look and measure commands know. */
val EXAMPLES: Set<String> get() = FIXTURES.keys

/**
 * The example [name]: as its fixture's commands make it, or with [fromFile] as its saved file under [examples] holds it, which
 * the golden tests keep equal to the fixture.
 */
fun exampleDocument(name: String, fromFile: Boolean, examples: File): SquashDocument =
    if (fromFile) readDocument(File(examples, "$name.json").readText())
    else requireNotNull(FIXTURES[name]) { "No example $name; known: ${FIXTURES.keys.sorted()}" }()

/** The named poses of example [name], an empty map for one with none. */
fun examplePoses(name: String): Map<String, Pose> = POSES[name] ?: emptyMap()
