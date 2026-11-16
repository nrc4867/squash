package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.shell.SquashFormat

/** Where the saved cat lies among the test fixtures' resources, which take in the project's examples folder. */
const val CAT_RESOURCE = "cat.json"

/** The character before any command: a name on the canine plan, which carries any four-legged body. */
val EMPTY_CAT = SquashDocument(Character(CharacterId("cat"), "Cat", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))

/** Every command that makes the cat: its body, coat, face and display pose. */
fun catCommands(): List<DocumentCommand> = catBody() + catColor() + catFace() + SetDisplayPose(CAT_DISPLAY)

/** The cat as those commands leave it. */
fun catDocument(): SquashDocument = catCommands().fold(EMPTY_CAT) { document, command -> command.apply(document).document }

/** [document] as the example file holds it: what File, Save writes. */
fun catText(document: SquashDocument): String = SquashFormat().plainText(document) + "\n"

/** The cat from the example file. */
fun savedCat(): SquashDocument =
    readDocument(checkNotNull(object {}.javaClass.classLoader.getResource(CAT_RESOURCE)) { "No $CAT_RESOURCE" }.readText())
