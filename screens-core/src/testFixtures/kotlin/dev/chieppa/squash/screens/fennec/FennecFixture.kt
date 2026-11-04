package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import kotlinx.serialization.json.Json

/** Where the saved document lies among the test fixtures' resources. */
const val FENNEC_RESOURCE = "fixtures/fennec.document.json"

/** The character before any command: a name on the canine plan. */
val EMPTY_FENNEC = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))

/** Every command that makes the fennec, from drawn outlines to surface work, in the order a person would send them. */
fun fennecCommands(): List<DocumentCommand> = fennecBody() + fennecColor() + fennecMarks()

/** The fennec as those commands leave it. */
fun fennecDocument(): SquashDocument = fennecCommands().fold(EMPTY_FENNEC) { document, command -> command.apply(document).document }

/** [document] as the saved fixture holds it. */
fun fennecText(document: SquashDocument): String = Json.encodeToString(SquashDocument.serializer(), document) + "\n"

/** The fennec from the saved fixture. */
fun savedFennec(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(FENNEC_RESOURCE)) { "No $FENNEC_RESOURCE" }.readText())
