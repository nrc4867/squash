package dev.chieppa.squash.screens

import dev.chieppa.squash.screens.sheet.PaneNavigation
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.rig.SessionPose
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
import dev.chieppa.squash.screens.rig.RigEditState
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.references.ReferenceEditState
import dev.chieppa.squash.screens.references.ReferenceImageCache
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.screens.shell.RestPoseArtist
import dev.chieppa.squash.screens.shell.SquashFormat
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.shell.stage
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.plan
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
import java.util.EnumMap
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

    /** The open character in its pose, on the session's graph; it changes with [session] and is set first. */
    val posing = MutableStateFlow<SessionPose?>(null)
    val selectedPart = MutableStateFlow<PartId?>(null)

    /** The open character's file and saved state, over the session's history. */
    val file = MutableStateFlow<DocumentSession<SquashDocument>?>(null)

    val tool = MutableStateFlow(BuildTool.DRAW_OUTLINE)

    /** The stage of Build on screen, which follows the armed tool and decides the strip, the keys and the inspector. */
    val stage = MutableStateFlow(Stage.SHAPE)

    /** The tool each stage was left holding, which coming back to the stage arms again. */
    private val armedIn = Stage.entries.associateWithTo(EnumMap(Stage::class.java)) { it.first }

    /** How the workspace lays out its views. */
    val layout = MutableStateFlow(SheetLayout.SHEET)

    /** The pane Focus shows large, and the pane under the pointer, which Space swaps into its place. */
    val focused = MutableStateFlow(SheetPane.SIDE)

    /** The reference images decoded for the drawers and panes. */
    val images = ReferenceImageCache()
    val references = ReferenceEditState()
    val hoveredPane = MutableStateFlow<SheetPane?>(null)

    /** The view the locked pane under the pointer holds, or null over Full, Single's free camera or nothing. */
    val hoveredView = MutableStateFlow<View?>(null)

    /** Puts the pane under the pointer in Focus's large place. */
    fun swapPane() {
        hoveredPane.value?.let { focused.value = it }
    }

    /** Shows the pane under the pointer at its fit: a locked pane's zoom and pan go, and a free camera frames the part again. */
    fun fitUnderPointer() {
        if (hoveredPane.value == null) return
        val view = hoveredView.value
        if (view != null) paneNavigation.fit(view) else paneNavigation.fitFree()
    }

    /** Whether edits of a paired part write the pair, which both sides share, or only the selected side's overrides. */
    val mirror = MutableStateFlow(true)

    /** The view the curve tools show their points in, which rings its pane. */
    val activeView = MutableStateFlow(View.SIDE)

    /** Each locked pane's own zoom and pan. */
    val paneNavigation = PaneNavigation()

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

    /** What the Rig stage remembers: the picked bone and seam, the brush and the wiggle playing. */
    val rig = RigEditState()

    /** The open socket Add part's popover is open on, and the type it would add there. */
    val addingSocket = MutableStateFlow<OpenSocket?>(null)
    val addingType = MutableStateFlow<TemplateId?>(null)

    private var sessionJob: Job? = null

    init {
        scope.launch(Dispatchers.Unconfined) {
            tool.collect { armed ->
                armed.stage?.let { stage.value = it }
                armedIn[stage.value] = armed
            }
        }
    }

    /** Shows [next], arming the tool it was left holding. */
    fun showStage(next: Stage) {
        if (next == stage.value) return
        paint.selectedRegion = null
        stage.value = next
        tool.value = armedIn.getValue(next)
    }

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
        rig.clear()
        posing.value = SessionPose(opened, held = rig.held)
        session.value = opened
        selectedPart.value = document.character.parts.firstOrNull()?.id
        mirror.value = true
        activeView.value = View.SIDE
        closeAddPart()
        curves.clear()
        spine.clear()
        paint.clear()
        lines.clear()
        references.clear()
        face.clear()
        // Undo can remove the selected part or the parent of the socket Add part is open on, so both follow the document.
        sessionScope.launch(Dispatchers.Unconfined) {
            opened.document.collect { current ->
                if (selectedPart.value?.let(current.character::part) == null) selectedPart.value = current.character.parts.firstOrNull()?.id
                val plan = templates.plan(current.character.bodyPlan)
                if (plan != null && addingSocket.value?.let { it in openSockets(current, plan, templates) } == false) closeAddPart()
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
        closeAddPart()
        curves.clear()
        spine.clear()
        lines.clear()
        references.clear()
        face.clear()
        rig.clear()
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
        screen.value = Screen.WORKSPACE
    }

    /** Opens Add part's popover on [socket], with the first type it takes chosen. */
    fun offerSocket(socket: OpenSocket) {
        addingSocket.value = socket
        addingType.value = socket.takes.first()
    }

    fun closeAddPart() {
        addingSocket.value = null
        addingType.value = null
    }

    /** The sockets of the open character with nothing on them. */
    fun openSocketsNow(): List<OpenSocket> {
        val document = session.value?.document?.value ?: return emptyList()
        val plan = templates.plan(document.character.bodyPlan) ?: return emptyList()
        return openSockets(document, plan, templates)
    }

    /** Arms [next], showing its stage, or puts the tool down when it is already armed, which leaves the panes to look at. */
    fun armTool(next: BuildTool) {
        tool.value = if (tool.value == next) BuildTool.NONE else next
        if (tool.value != BuildTool.REGION_BORDER) paint.selectedRegion = null
    }

    /** Puts the armed tool down, which leaves a press on the character to select its part. */
    fun putToolDown() {
        tool.value = BuildTool.NONE
        paint.selectedRegion = null
    }

    /**
     * Adds the type chosen in Add part's popover on its socket, both sides of a pair in one step, selects it and closes the popover.
     * Only a type the socket takes is added.
     */
    fun addPart() {
        val opened = session.value ?: return
        val socket = addingSocket.value ?: return
        val template = addingType.value?.takeIf { it in socket.takes }?.let { templates.template(it) } ?: return
        val start = templates.plan(opened.document.value.character.bodyPlan)?.startingData() ?: PartData()
        val parts = placeOnSocket(socket, template, opened.document.value, start)
        opened.send(AddParts(parts, pairs = parts.mapNotNull { it.pair }.associateWith { start }))
        closeAddPart()
        selectPart(parts.first().id)
    }
}
