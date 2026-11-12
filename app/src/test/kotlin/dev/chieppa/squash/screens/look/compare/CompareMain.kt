package dev.chieppa.squash.screens.look.compare

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.screens.look.shots.REFERENCE_GROUND
import dev.chieppa.squash.screens.look.shots.SHOTS_FOLDER
import dev.chieppa.squash.screens.look.shots.shotList
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** The overlay's colours: what only the reference covers, what only Squash covers, what both cover, and neither. */
private const val REFERENCE_ONLY = 0xFF2F6FD6.toInt()
private const val SQUASH_ONLY = 0xFFE0532F.toInt()
private const val SHARED = 0xFF9C9C9C.toInt()
private const val NEITHER = 0xFFFFFFFF.toInt()

/** The background both renders share, as an ARGB colour. */
private val GROUND = REFERENCE_GROUND.toArgb()

/** The gap between the reference and the Squash render in a pair, in pixels, and its colour. */
private const val GAP = 8
private const val GAP_COLOR = 0xFF2A1B17.toInt()

private const val LOOK_FOLDER = "app/build/look"
private const val COMPARE_FOLDER = "app/build/compare"
private const val TABLE = "iou.txt"

/**
 * Compares an example's shots, as the look command rendered them with `shots=`, with the reference renders of the same names: per shot a
 * side-by-side (`<shot>-pair.png`, the reference on the left) and a silhouette overlay (`<shot>-overlay.png`), and the silhouette's
 * intersection over union, whole and per body band, printed and kept as a table. Each silhouette is what is not the reference's flat
 * background, which the shots render on too, so a close-up whose corner shows cloth or skin still keys the character alone.
 * Run as `./gradlew :app:compare -Pcompare="example=woman-twin-dressed reference=<folder of the reference PNGs>"`.
 */
fun main(args: Array<String>) {
    val values = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val root = File(values["root"] ?: ".")
    val example = values["example"] ?: "woman-twin-dressed"
    val reference = File(requireNotNull(values["reference"]) { "compare needs reference=<folder of the reference renders>" })
    val squash = File(root, "$LOOK_FOLDER/$example/$SHOTS_FOLDER")
    val out = File(values["out"] ?: File(root, "$COMPARE_FOLDER/$example").path).apply { mkdirs() }
    val rows = mutableListOf<List<String>>()
    val missing = mutableListOf<String>()
    for (shot in shotList(values["shots"] ?: "reference")) {
        val refFile = File(reference, "${shot.name}.png")
        val ownFile = File(squash, "${shot.name}.png")
        if (!refFile.exists() || !ownFile.exists()) {
            missing += shot.name
            continue
        }
        val ref = ImageIO.read(refFile)
        val own = ImageIO.read(ownFile)
        ImageIO.write(sideBySide(ref, own), "png", File(out, "${shot.name}-pair.png"))
        val a = Silhouette.keyed(ref, GROUND)
        val b = Silhouette.keyed(own, GROUND)
        require(a.width == b.width && a.height == b.height) { "${shot.name}: the reference is ${a.width}x${a.height}, the render ${b.width}x${b.height}" }
        ImageIO.write(overlay(a, b), "png", File(out, "${shot.name}-overlay.png"))
        val bands = BodyBands.of(shot)
        val whole = intersectionOverUnion(a, b)
        val regions = Region.entries.map { region ->
            if (bands == null || region !in bands.regions) null else intersectionOverUnion(a, b) { x, y -> bands.inside(region, x, y) }
        }
        rows += listOf(shot.name, whole.format()) + regions.map { it.format() }
    }
    val table = buildString {
        appendLine("| shot | whole | " + Region.entries.joinToString(" | ") { it.name.lowercase() } + " |")
        appendLine("|---|---|" + Region.entries.joinToString("") { "---|" })
        rows.forEach { appendLine("| " + it.joinToString(" | ") + " |") }
        val wholes = rows.mapNotNull { it[1].toFloatOrNull() }
        if (wholes.isNotEmpty()) appendLine("| mean | %.3f |".format(wholes.average()) + " |".repeat(Region.entries.size))
    }
    File(out, TABLE).writeText(table)
    print(table)
    missing.forEach { System.err.println("compare: no pair for $it") }
    println("compare: ${rows.size} shots into ${out.absolutePath}")
    exitProcess(if (rows.isEmpty()) 1 else 0)
}

private fun Float?.format() = this?.let { "%.3f".format(it) } ?: "-"

/** [left] and [right] side by side, top-aligned, with a thin gap. */
private fun sideBySide(left: BufferedImage, right: BufferedImage): BufferedImage {
    val out = BufferedImage(left.width + GAP + right.width, maxOf(left.height, right.height), BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until out.height) for (x in left.width until left.width + GAP) out.setRGB(x, y, GAP_COLOR)
    out.createGraphics().apply {
        drawImage(left, 0, 0, null)
        drawImage(right, left.width + GAP, 0, null)
        dispose()
    }
    return out
}

private fun overlay(reference: Silhouette, squash: Silhouette): BufferedImage {
    val out = BufferedImage(reference.width, reference.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until reference.height) for (x in 0 until reference.width) {
        val a = reference[x, y]
        val b = squash[x, y]
        out.setRGB(x, y, if (a && b) SHARED else if (a) REFERENCE_ONLY else if (b) SQUASH_ONLY else NEITHER)
    }
    return out
}
