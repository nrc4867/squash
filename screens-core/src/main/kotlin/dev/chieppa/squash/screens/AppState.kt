package dev.chieppa.squash.screens

import dev.chieppa.squash.screens.sheet.PaneNavigation
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.edit.CurveEditState
import dev.chieppa.squash.screens.edit.PaintEditState
import dev.chieppa.squash.screens.lines.FaceEditState
import dev.chieppa.squash.screens.lines.LineEditState
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.references.ReferenceEditState
import dev.chieppa.squash.screens.references.ReferenceImageCache
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.screens.shell.PickTool
import dev.chieppa.squash.screens.shell.RestPoseArtist
import dev.chieppa.squash.screens.shell.SquashFormat
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.socketFor
import dev.chieppa.squash.character.catalog.startingData
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.screens.spine.SpineEditState
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
    val format: SquashFormat = SquashFormat(),
    private val storage: DocumentStorage = FileStorage,
    /** What renders rest pose art offscreen; with none, a screen shows no art. */
    val artist: RestPoseArtist? = null,
) {
    val screen = MutableStateFlow(if (recent.entries.isEmpty()) Screen.NEW_CHARACTER else Screen.HOME)
    val session = MutableStateFlow<CharacterSession?>(null)
    val selectedPart = MutableStateFlow<PartId?>(null)

    /** The open character's file and saved state, over the session's history. */
    val file = MutableStateFlow<DocumentSession<SquashDocument>?>(null)

    val tool = MutableStateFlow(BuildTool.DRAW_OUTLINE)

    /** How the workspace lays out its views. */
    val layout = MutableStateFlow(SheetLayout.SHEET)

    /** The pane Focus shows large, and the pane under the pointer, which Space swaps into its place. */
    val focused = MutableStateFlow(SheetPane.SIDE)

    /** The reference images decoded for the drawers and panes. */
    val images = ReferenceImageCache()
    val references = ReferenceEditState()
    val hoveredPane = MutableStateFlow<SheetPane?>(null)

    /** Puts the pane under the pointer in Focus's large place. */
    fun swapPane() {
        hoveredPane.value?.let { focused.value = it }
    }

    /** Whether edits of a paired part write the pair, which both sides share, or only the selected side's overrides. */
    val mirror = MutableStateFlow(true)

    /** The view the curve tools show their points in, which rings its pane. */
    val activeView = MutableStateFlow(View.SIDE)

    /** Each locked pane's own zoom and pan. */
    val paneNavigation = PaneNavigation()

    /** What a socket's dot does on Pick a part type. */
    val pickTool = MutableStateFlow(PickTool.SELECT)

    /** Whether the theme is the dark one. */
    val darkTheme = MutableStateFlow(false)

    /** What the curve tools remember: selection, oversketch settings and readouts. */
    val curves = CurveEditState()

    /** What the paint tools remember: slot, brush and the border picked. */
    val paint = PaintEditState()

    /** The Spine and width tool's stroke, until it is written or dropped. */
    val spine = SpineEditState({ curves.stabilizer }, { curves.snapGuides }) { curves.smooth }

    /** What the Line width tool remembers. */
    val lines = LineEditState()

    /** What the face tools remember and the test gaze. */
    val face = FaceEditState()

    /** What Pick a part type has chosen so far. */
    val pickedSocket = MutableStateFlow<OpenSocket?>(null)
    val pickedType = MutableStateFlow<TemplateId?>(null)

    private var sessionJob: Job? = null

    /** Whether an edit writes the selected side alone, as [mirror] off asks. */
    val thisSide: Boolean get() = !mirror.value

    /** What an edit of the selected part in [view] starts from, which is where the edit will land. */
    fun editedOutline(view: View): OutlineCurve? {
        val character = session.value?.document?.value?.character ?: return null
        val part = selectedPart.value?.let(character::part) ?: return null
        return character.editBase(part, thisSide).outlines[view]
    }

    /** Makes [document] the open character. Throws when it pins a body plan or template that is not loaded. */
    fun open(document: SquashDocument, path: String? = null): CharacterSession {
        requireNotNull(templates.plan(document.character.bodyPlan)) { "No body plan ${document.character.bodyPlan.id.value}" }
        document.character.parts.forEach { part -> requireNotNull(templates.template(part.template)) { "No template ${part.template.id.value}" } }
        sessionJob?.cancel()
        val job = SupervisorJob(scope.coroutineContext[Job])
        sessionJob = job
        val sessionScope = scope + job
        val opened = CharacterSession(document, TemplateLibrary(templates.parts, templates.plans), sessionScope)
        file.value = DocumentSession(format, document, storage, path, opened.history)
        session.value = opened
        selectedPart.value = document.character.parts.firstOrNull()?.id
        mirror.value = true
        activeView.value = View.SIDE
        clearPick()
        curves.clear()
        spine.clear()
        paint.clear()
        lines.clear()
        references.clear()
        face.clear()
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
        curves.clear()
        spine.clear()
        lines.clear()
        references.clear()
        face.clear()
        screen.value = Screen.HOME
    }

    /** Takes back the last step, unless a drag holds a gesture open on the history. */
    fun undo() {
        session.value?.history?.takeUnless { it.inGesture }?.undo()
    }

    /** Brings back the last undone step, unless a drag holds a gesture open on the history. */
    fun redo() {
        session.value?.history?.takeUnless { it.inGesture }?.redo()
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

    /** Arms [next], or disarms the tool when it is already armed, which leaves the panes to look at and the template's drawer. */
    fun armTool(next: BuildTool) {
        tool.value = if (tool.value == next) BuildTool.NONE else next
        if (tool.value != BuildTool.REGION_BORDER) paint.selectedRegion = null
    }

    /** Places the chosen type on [socket], or the first it takes when it takes no such type. */
    fun placeOn(socket: OpenSocket) {
        val chosen = pickedType.value?.takeIf { it in socket.takes }
        chooseSocket(socket)
        if (chosen != null) pickedType.value = chosen
        placePicked()
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
        val start = templates.plan(opened.document.value.character.bodyPlan)?.startingData() ?: PartData()
        val parts = placeOnSocket(socket, template, opened.document.value, start)
        opened.send(AddParts(parts, pairs = parts.mapNotNull { it.pair }.associateWith { start }))
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
