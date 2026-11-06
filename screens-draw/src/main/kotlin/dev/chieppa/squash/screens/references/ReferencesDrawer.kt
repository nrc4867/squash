package dev.chieppa.squash.screens.references

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.SetReferenceImage
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.refs_add_images
import dev.chieppa.squash.uidesign.generated.refs_add_kinds
import dev.chieppa.squash.uidesign.generated.refs_choose_images
import dev.chieppa.squash.uidesign.generated.refs_delete
import dev.chieppa.squash.uidesign.generated.refs_drop_over
import dev.chieppa.squash.uidesign.generated.refs_drop_reject
import dev.chieppa.squash.uidesign.generated.refs_none
import dev.chieppa.squash.uidesign.generated.refs_pick_color
import dev.chieppa.squash.uidesign.generated.refs_picked
import dev.chieppa.squash.uidesign.generated.refs_picked_slot
import dev.chieppa.squash.uidesign.generated.refs_trace_in
import dev.chieppa.squash.uidesign.generated.shell_view_front
import dev.chieppa.squash.uidesign.generated.shell_view_side
import dev.chieppa.squash.uidesign.generated.shell_view_top
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.Dropzone
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Swatch
import dev.chieppa.stretch.ui.fileName
import dev.chieppa.stretch.ui.pickFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The image files the board takes. */
val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

/** The board's height before any image is on it, in board units. */
private const val EMPTY_BOARD_HEIGHT = 120f

/** How far into an image's corner its view chip sits, in board units. */
private const val CHIP_INSET = 4

object ReferencesTags {
    const val BOARD = "references.board"
    const val TRACE_IN = "references.traceIn"
    const val PICK_COLOR = "references.pickColor"
    const val DELETE = "references.delete"
    const val ADD = "references.add"

    fun image(id: ImageId) = "references.image.${id.value}"
}

/** The views Trace in offers, None first. */
private val TRACE_CHOICES: List<View?> = listOf(null, View.SIDE, View.FRONT, View.TOP)

/**
 * The References drawer: the board, where images are laid out freely and dragged about, a picked one ringed; the picked image's
 * Trace in, which places it in one of the character's views, and the colors picked from it, with the eyedropper that adds more
 * to the palette; and Add images, which takes files dropped on it or chosen.
 */
@Composable
fun ReferencesDrawer(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val picked = state.references.picked?.let(document.references::image)
    Column {
        Board(state, open, document)
        if (picked != null) PickedSection(state, open, document, picked)
        AddImages(state, open)
    }
}

@Composable
private fun Board(state: AppState, session: CharacterSession, document: SquashDocument) {
    val images = document.references.images
    val height = if (images.isEmpty()) EMPTY_BOARD_HEIGHT else boardBottom(images) + BOARD_GAP
    Box(Modifier.fillMaxWidth().height(height.dp).testTag(ReferencesTags.BOARD)) {
        for (image in images) BoardImage(state, session, document, image)
    }
}

@Composable
private fun BoardImage(state: AppState, session: CharacterSession, document: SquashDocument, image: ReferenceImage) {
    val decoded = state.images.of(image)
    val place = image.board
    val tall = place.width * image.height / image.width
    val chosen = state.references.picked == image.id
    val density = LocalDensity.current.density
    val labels = viewLabels()
    val traced = tracedIn(document.character.traces, image.id).firstOrNull()
    val edge = if (chosen) Stretch.colors.ringSelected else Stretch.colors.lineStrong
    val label = stringResource(Res.string.refs_picked_slot, image.name, pickedCount(document, image.id) + 1)
    Box(
        Modifier
            .offset(place.x.dp, place.y.dp)
            .size(place.width.dp, tall.dp)
            .background(Stretch.colors.surfaceWell)
            .border(if (chosen) 2.dp else 1.dp, edge)
            .testTag(ReferencesTags.image(image.id))
            .pointerInput(image.id, image.board, state.references.picks) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    state.references.picked = image.id
                    if (state.references.picks) {
                        val color = decoded?.colorAt(down.position.x / size.width, down.position.y / size.height) ?: return@awaitEachGesture
                        pickedColorCommand(session.document.value, image.id, SlotColor(color.red, color.green, color.blue), label)?.let(session::send)
                        return@awaitEachGesture
                    }
                    var gesture: History<SquashDocument>.Transaction? = null
                    var released = false
                    try {
                        released = followToRelease(down.id, {}) { pixel ->
                            val moved = (pixel - down.position) / density
                            val board = place.copy(x = (place.x + moved.x).coerceAtLeast(0f), y = (place.y + moved.y).coerceAtLeast(0f))
                            (gesture ?: session.history.begin().also { gesture = it }).update(SetReferenceImage(image.id, image.copy(board = board)))
                        }
                    } finally {
                        gesture?.let { if (released) it.commit() else it.cancel() }
                    }
                }
            },
    ) {
        if (decoded != null) Image(decoded.bitmap, image.name, Modifier.size(place.width.dp, tall.dp), contentScale = ContentScale.FillBounds)
        if (traced != null) Chip(labels.getValue(traced), Modifier.align(Alignment.TopEnd).padding(CHIP_INSET.dp), kind = ChipKind.On)
    }
}

@Composable
private fun viewLabels(): Map<View, String> = mapOf(
    View.SIDE to stringResource(Res.string.shell_view_side),
    View.FRONT to stringResource(Res.string.shell_view_front),
    View.TOP to stringResource(Res.string.shell_view_top),
)

@Composable
private fun PickedSection(state: AppState, session: CharacterSession, document: SquashDocument, image: ReferenceImage) {
    val labels = viewLabels()
    val none = stringResource(Res.string.refs_none)
    val traced = tracedIn(document.character.traces, image.id).firstOrNull()
    val pick = stringResource(Res.string.refs_pick_color)
    val delete = stringResource(Res.string.refs_delete)
    DrawerSection(image.name) {
        SettingRow(stringResource(Res.string.refs_trace_in)) {
            Spacer(Modifier.weight(1f))
            Segmented(TRACE_CHOICES, traced, { view ->
                val box = session.assembly.output.value?.box
                traceInCommand(session.document.value, image.id, view, box)?.let(session::send)
            }, { it?.let(labels::getValue) ?: none }, Modifier.testTag(ReferencesTags.TRACE_IN))
        }
        SettingRow(stringResource(Res.string.refs_picked)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                for (name in image.picked) {
                    val slot = document.character.palette.slots.firstOrNull { it.name == name } ?: continue
                    Swatch(Color(slot.color.red, slot.color.green, slot.color.blue), slot.label ?: slot.name.value, false, {})
                }
            }
            IconButton(SquashIcons.PickColor, pick, { state.references.picks = !state.references.picks }, Modifier.testTag(ReferencesTags.PICK_COLOR), on = state.references.picks)
            IconButton(SquashIcons.RemoveSlot, delete, {
                session.send(deleteImageCommand(image.id))
                state.references.clear()
            }, Modifier.testTag(ReferencesTags.DELETE))
        }
    }
}

@Composable
private fun AddImages(state: AppState, session: CharacterSession) {
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.string.refs_add_images)
    fun add(paths: List<String>) {
        scope.launch {
            for (path in paths) {
                val bytes = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() } ?: continue
                val decoded = decodeImage(bytes) ?: continue
                val command = newImageCommand(session.document.value, fileName(path).substringBeforeLast('.'), bytes, decoded.width, decoded.height)
                session.send(command)
                state.references.picked = command.id
            }
        }
    }
    DrawerSection(divider = false) {
        Dropzone(
            label,
            stringResource(Res.string.refs_add_kinds),
            IMAGE_EXTENSIONS,
            ::add,
            { scope.launch { add(pickFiles(getString(Res.string.refs_choose_images), IMAGE_EXTENSIONS)) } },
            stringResource(Res.string.refs_drop_over),
            stringResource(Res.string.refs_drop_reject),
            Modifier.fillMaxWidth().testTag(ReferencesTags.ADD),
        )
    }
}
