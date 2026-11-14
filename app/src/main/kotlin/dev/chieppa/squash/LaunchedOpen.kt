package dev.chieppa.squash

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.shell.Stage
import java.io.File

/**
 * Opens what the launch names, for looking at a state of the app without going through it: the file `squash.open` names in the
 * workspace, in the stage `squash.stage` names, with the part `squash.select` names selected. Nothing without `squash.open`.
 */
internal fun AppState.openAsLaunched() {
    val path = System.getProperty("squash.open") ?: return
    open(readDocument(File(path).readText()), path)
    screen.value = Screen.WORKSPACE
    System.getProperty("squash.stage")?.let { showStage(Stage.valueOf(it)) }
    System.getProperty("squash.select")?.let { selectPart(PartId(it)) }
}
