package dev.chieppa.squash.screens

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.OpenSocket
import dev.chieppa.squash.screens.shell.ScratchFormat
import dev.chieppa.squash.screens.shell.newCharacter
import dev.chieppa.squash.screens.shell.openSockets
import dev.chieppa.squash.screens.shell.placeOnSocket
import dev.chieppa.squash.screens.shell.plan
import dev.chieppa.squash.screens.shell.socketFor
import dev.chieppa.squash.screens.shell.template
import dev.chieppa.stretch.document.DocumentSession
import dev.chieppa.stretch.document.DocumentStorage
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Session state above the document: where the window is, what is open and what is selected. Never saved or undone.
 * First launch has no recent files and opens straight on New character, so Home always has something to show.
 */
class AppState(
    val templates: TemplateFolder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val recent: RecentFiles = RecentFiles(),
    val format: ScratchFormat = ScratchFormat(),
    private val storage: DocumentStorage = FileStorage,
) {
    val screen = MutableStateFlow(if (recent.entries.isEmpty()) Screen.NEW_CHARACTER else Screen.HOME)
    val session = MutableStateFlow<CharacterSession?>(null)
    val selectedPart = MutableStateFlow<PartId?>(null)

    /** The open character's file and saved state, over the session's history. */
    val file = MutableStateFlow<DocumentSession<SquashDocument>?>(null)

    val tool = MutableStateFlow(BuildTool.DRAW_OUTLINE)

    /** What Pick a part type has chosen so far. */
    val pickedSocket = MutableStateFlow<OpenSocket?>(null)
    val pickedType = MutableStateFlow<TemplateId?>(null)

    private var sessionJob: Job? = null

    /** Makes [document] the open character. Throws when it pins a body plan or template that is not loaded. */
    fun open(document: SquashDocument, path: String? = null): CharacterSession {
        requireNotNull(templates.plan(document.character.bodyPlan)) { "No body plan ${document.character.bodyPlan.id.value}" }
        document.character.parts.forEach { part -> requireNotNull(templates.template(part.template)) { "No template ${part.template.id.value}" } }
        sessionJob?.cancel()
        val job = SupervisorJob(scope.coroutineContext[Job])
        sessionJob = job
        val sessionScope = scope + job
        val opened = CharacterSession(document, TemplateLibrary(templates.parts), sessionScope)
        file.value = DocumentSession(format, document, storage, path, opened.history)
        session.value = opened
        selectedPart.value = document.character.parts.firstOrNull()?.id
        clearPick()
        // Undo can remove the selected part or a picked socket's parent, so both follow the document.
        sessionScope.launch(Dispatchers.Unconfined) {
            opened.document.collect { current ->
                if (selectedPart.value?.let(current.character::part) == null) selectedPart.value = current.character.parts.firstOrNull()?.id
                val plan = templates.plan(current.character.bodyPlan)
                if (plan != null && pickedSocket.value?.let { it in openSockets(current, plan, templates) } == false) pickedSocket.value = null
            }
        }
        return opened
    }

    /** Opens a new character from [plan], placing its root part through commands, and goes to the workspace. */
    fun createCharacter(plan: BodyPlan, name: String) {
        val new = newCharacter(plan, templates, name)
        val opened = open(new.document)
        new.commands.forEach(opened::send)
        selectedPart.value = new.root
        screen.value = Screen.WORKSPACE
    }

    fun closeCharacter() {
        sessionJob?.cancel()
        sessionJob = null
        session.value = null
        file.value = null
        selectedPart.value = null
        clearPick()
        screen.value = Screen.HOME
    }

    /** Runs [then] once the open character's changes are safe, asking first when there are any. */
    fun whenSaved(then: () -> Unit) {
        val open = file.value
        if (open != null) open.whenSaved(then) else then()
    }

    fun selectPart(part: PartId) {
        selectedPart.value = part
        clearPick()
        screen.value = Screen.WORKSPACE
    }

    /** Goes to Pick a part type for [socket], with the first type it takes chosen. */
    fun chooseSocket(socket: OpenSocket) {
        pickedSocket.value = socket
        pickedType.value = socket.takes.first()
        screen.value = Screen.PICK_PART
    }

    /** Goes to Pick a part type from the rail: the first open socket, or just the first type when none is open. */
    fun openPartTypes() {
        val socket = currentSockets().firstOrNull()
        if (socket != null) {
            chooseSocket(socket)
        } else {
            pickedType.value = templates.parts.firstOrNull()?.id
            screen.value = Screen.PICK_PART
        }
    }

    /** Chooses a type, moving to a socket that takes it; with no such socket, nothing can be placed. */
    fun chooseType(type: TemplateId) {
        pickedType.value = type
        pickedSocket.value = socketFor(type, currentSockets(), pickedSocket.value)
    }

    /** Sends the picked type onto the picked socket, all sides in one step, selects it and goes to the workspace. */
    fun placePicked() {
        val opened = session.value ?: return
        val socket = pickedSocket.value ?: return
        val template = pickedType.value?.let { templates.template(it) } ?: return
        val parts = placeOnSocket(socket, template, opened.document.value)
        opened.send(AddParts(parts))
        selectPart(parts.first().id)
    }

    private fun currentSockets(): List<OpenSocket> {
        val document = session.value?.document?.value ?: return emptyList()
        val plan = templates.plan(document.character.bodyPlan) ?: return emptyList()
        return openSockets(document, plan, templates)
    }

    private fun clearPick() {
        pickedSocket.value = null
        pickedType.value = null
    }
}
