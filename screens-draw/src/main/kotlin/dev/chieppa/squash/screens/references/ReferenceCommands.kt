package dev.chieppa.squash.screens.references

import dev.chieppa.squash.document.BoardPlace
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.ImageBytes
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetReferenceImage
import dev.chieppa.squash.document.SetTrace
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.Ulid
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox

/** The board's margin and the gap between images on it, in board units. */
const val BOARD_GAP = 12f

/** How wide an image lands on the board at most, in board units. */
const val BOARD_IMAGE_WIDTH = 236f

private const val PICKED_SLOT_PREFIX = "picked-"

/** The lowest point any image on the board reaches, in board units, holding its [height] over its width. */
fun boardBottom(images: List<ReferenceImage>): Float = images.maxOfOrNull { it.board.y + it.board.width * it.height / it.width } ?: 0f

/**
 * A new reference image named [name] from a file's [bytes] decoded at [width] by [height] pixels, set on the board under the
 * images already there.
 */
fun newImageCommand(document: SquashDocument, name: String, bytes: ByteArray, width: Int, height: Int, id: ImageId = ImageId(Ulid.next())): SetReferenceImage {
    val images = document.references.images
    val top = if (images.isEmpty()) BOARD_GAP else boardBottom(images) + BOARD_GAP
    val image = ReferenceImage(id, name, ImageBytes(bytes), width, height, BoardPlace(BOARD_GAP, top, minOf(BOARD_IMAGE_WIDTH, width.toFloat())))
    return SetReferenceImage(id, image)
}

/**
 * What traces [image] in [view] alone, or in none with null: it leaves any other view it was traced in, keeps its place where it
 * already is, and fits over the character in [box] where it is new. Null when nothing would change.
 */
fun traceInCommand(document: SquashDocument, image: ImageId, view: View?, box: BodyBox?): DocumentCommand? {
    val traces = document.character.traces
    val picture = document.references.image(image) ?: return null
    val leaving = traces.filter { (at, trace) -> trace.image == image && at != view }.keys.map { SetTrace(it, null) }
    val arriving = view?.takeIf { traces[it]?.image != image }?.let { SetTrace(it, fittedTrace(picture, it, box)) }
    val commands = leaving + listOfNotNull(arriving)
    return when (commands.size) {
        0 -> null
        1 -> commands.single()
        else -> Together(commands)
    }
}

/** Takes [image] off the board, with the traces that show it. */
fun deleteImageCommand(image: ImageId): DocumentCommand = SetReferenceImage(image, null)

/** A new palette slot of [color] labelled [label], kept among the colors picked from [image]. */
fun pickedColorCommand(document: SquashDocument, image: ImageId, color: SlotColor, label: String): DocumentCommand? {
    val picture = document.references.image(image) ?: return null
    val taken = document.character.palette.slots.mapTo(hashSetOf()) { it.name.value }
    val name = generateSequence(1) { it + 1 }.map { SlotName("$PICKED_SLOT_PREFIX$it") }.first { it.value !in taken }
    return Together(listOf(SetPaletteSlot(name, color, label = label), SetReferenceImage(image, picture.copy(picked = picture.picked + name))))
}

/** How many colors have been picked from [image] so far, which numbers the next one's label. */
fun pickedCount(document: SquashDocument, image: ImageId): Int = document.references.image(image)?.picked?.size ?: 0
