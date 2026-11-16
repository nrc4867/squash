package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetOneSurface
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.shell.SquashFormat

/** Where the saved document lies among the test fixtures' resources, which take in the project's examples folder. */
const val FENNEC_RESOURCE = "fennec.json"

/** The character before any command: a name on the canine plan. */
val EMPTY_FENNEC = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))

/**
 * Every command that makes the fennec, from drawn outlines to surface work, in the order a person would send them, then its painted
 * eyes.
 */
fun fennecCommands(): List<DocumentCommand> = fennecBody() + fennecColor() + fennecMarks() + fennecPaintedEyes()

/** The fennec as those commands leave it. */
fun fennecDocument(): SquashDocument = fennecCommands().fold(EMPTY_FENNEC) { document, command -> command.apply(document).document }

/** The fennec built as one surface, as its commands build it. */
fun fennecOneSurfaceDocument(): SquashDocument = fennecDocument()

/** [this] built as its parts' own meshes rather than as one surface, for what only a character built that way shows. */
fun SquashDocument.asParts(): SquashDocument = SetOneSurface(false).apply(this).document

/** [document] as the example file holds it: what File, Save writes. */
fun fennecText(document: SquashDocument): String = SquashFormat().plainText(document) + "\n"

/** The fennec from the example file. */
fun savedFennec(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(FENNEC_RESOURCE)) { "No $FENNEC_RESOURCE" }.readText())
