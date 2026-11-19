package dev.chieppa.squash.screens.look.spike

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.NewGarmentNames
import dev.chieppa.squash.screens.dress.PaintStyle
import dev.chieppa.squash.screens.dress.newAreaCommand
import dev.chieppa.squash.screens.dress.newGarmentCommand
import dev.chieppa.squash.screens.dress.newGarmentIn
import dev.chieppa.squash.screens.dress.paintCommand
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.look.exampleDocument
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.woman.womanDocument
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private val AZIMUTHS = listOf(0f, 40f)
private val EXAMPLE_AZIMUTHS = listOf(0f, 40f, 90f)

/** Each character the shapes are painted on, its document and its shapes. */
private val SUBJECTS: Map<String, Pair<() -> SquashDocument, List<SpikeShape>>> = mapOf(
    "woman" to (::womanDocument to WOMAN_SHAPES),
    "fennec" to (::fennecDocument to FENNEC_SHAPES),
    "woman-dressed" to ({ exampleDocument("woman-dressed", false, File("examples")) } to WOMAN_SHAPES),
    "fennec-dressed" to ({ exampleDocument("fennec-dressed", false, File("examples")) } to FENNEC_SHAPES),
)

/** The pose each subject is also looked at in, dressed. */
private val POSED: Map<String, dev.chieppa.squash.document.Pose> = mapOf(
    "woman" to dev.chieppa.squash.screens.woman.WOMAN_POSES.getValue("walk"),
    "fennec" to dev.chieppa.squash.screens.fennec.FENNEC_POSES.getValue("walk-2"),
    "woman-dressed" to dev.chieppa.squash.screens.woman.WOMAN_POSES.getValue("arm-up"),
    "fennec-dressed" to dev.chieppa.squash.screens.fennec.FENNEC_POSES.getValue("walk-2"),
)

private fun args(raw: Array<String>): Map<String, String> = raw.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

/**
 * The garment coverage spike: `mode=render` paints each shape on each subject as a new garment and keeps the painted cells beside the
 * cloth, `mode=time` times paint strokes on a dressed example, `mode=examples` renders the dressed examples.
 */
fun main(raw: Array<String>) {
    val a = args(raw)
    val out = File(a["out"] ?: "spike")
    val errors = ArrayList<String>()
    printGrids = a["grid"] == "true"
    when (a["mode"] ?: "render") {
        "render" -> renderShapes(out, a["subjects"]?.split(',') ?: SUBJECTS.keys.toList(), a["shapes"]?.split(','), errors, a["images"] != "false", a["frame"]?.split(",")?.mapTo(HashSet()) { dev.chieppa.squash.document.PartId(it) }, a["suffix"] ?: "")
        "examples" -> renderExamples(out, a["examples"]?.split(',') ?: listOf("woman-dressed", "fennec-dressed", "cat-dressed", "woman-twin-dressed"), File(a["root"] ?: "."), errors)
        "build" -> timeBuilds(a["examples"]?.split(',') ?: listOf("fennec", "woman"), mapOf("fennec" to FENNEC_SHAPES, "woman" to WOMAN_SHAPES), a["runs"]?.toInt() ?: 3, File(a["root"] ?: "."))
        "time" -> timeStrokes(a["examples"]?.split(',') ?: listOf("woman-dressed", "fennec-dressed"), a["runs"]?.toInt() ?: 5, File(a["root"] ?: "."))
    }
    errors.forEach { System.err.println(it) }
    exitProcess(if (errors.isEmpty()) 0 else 1)
}

/** Sends a new garment over [session]'s character, then paints [areas] into it, each into an area of its own; the garment. */
fun paintGarment(work: TestWork?, session: CharacterSession, areas: (Character, AssembledCharacter) -> List<SurfaceMask>, settle: () -> Unit): Garment {
    val character = session.document.value.character
    val garment = newGarmentIn(character.wardrobe) { "Spike" }
    session.send(character.newGarmentCommand(garment, null, NewGarmentNames("Spike", "Spike")))
    settle()
    val body0 = checkNotNull(session.assembly.output.value)
    val masks = areas(session.document.value.character, body0)
    var area: GarmentAreaId? = null
    masks.forEachIndexed { i, mask ->
        val now = session.document.value.character
        val body = checkNotNull(session.assembly.output.value)
        var held = checkNotNull(now.wardrobe.garment(garment.id))
        if (i > 0) {
            val (command, id) = now.newAreaCommand(body, held)
            session.send(command)
            settle()
            area = id
            held = checkNotNull(session.document.value.character.wardrobe.garment(garment.id))
        }
        val command: DocumentCommand = session.document.value.character.paintCommand(checkNotNull(session.assembly.output.value), held, area, mask, PaintStyle())
        session.send(command)
        settle()
    }
    return checkNotNull(session.document.value.character.wardrobe.garment(garment.id))
}

private fun renderShapes(out: File, subjects: List<String>, only: List<String>?, errors: MutableList<String>, images: Boolean, framing: Set<dev.chieppa.squash.document.PartId>? = null, suffix: String = "") {
    out.mkdirs()
    for (subject in subjects) {
        val (document, shapes) = SUBJECTS.getValue(subject)
        for (shape in shapes.filter { only == null || it.name in only }) {
            TestWork().use { work ->
                val session = CharacterSession(document(), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
                work.settle(session)
                if (!images) {
                    val garment = paintGarment(work, session, shape.areas) { work.settle(session) }
                    fidelity(session, garment, "$subject ${shape.name}")
                    return@use
                }
                val (bare, bareCameras) = renderRow(work, session, AZIMUTHS, errors, framing)
                val bareCharacter = session.document.value.character
                val bareBody = checkNotNull(session.assembly.output.value)
                val garment = paintGarment(work, session, shape.areas) { work.settle(session) }
                fidelity(session, garment, "$subject ${shape.name}")
                val cells = areaCells(garment)
                drawCells(bare, bareCameras, bareCharacter, bareBody, cells)
                val (dressed, cameras) = renderRow(work, session, AZIMUTHS, errors, framing)
                val edged = BufferedImage(dressed.width, dressed.height, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(dressed, 0, 0, null); dispose() } }
                drawEdges(edged, cameras, session.document.value.character, checkNotNull(session.assembly.output.value), cells)
                val row = BufferedImage(bare.width * 3, TILE_HEIGHT + LABEL, BufferedImage.TYPE_INT_ARGB)
                val g = row.createGraphics()
                g.color = Color.WHITE
                g.fillRect(0, 0, row.width, row.height)
                g.color = Color.BLACK
                g.font = Font(Font.SANS_SERIF, Font.BOLD, 22)
                g.drawString("$subject  ${shape.name}   painted cells  |  cloth, hem and seam ink  |  cloth with the painted cells' edge drawn over it", 12, 28)
                g.drawImage(bare, 0, LABEL, null)
                g.drawImage(dressed, bare.width, LABEL, null)
                g.drawImage(edged, bare.width * 2, LABEL, null)
                g.dispose()
                ImageIO.write(row, "png", File(out, "$subject-${shape.name}$suffix.png"))
                POSED[subject]?.let { pose ->
                    session.send(dev.chieppa.squash.document.SetPose(pose))
                    val (posed, _) = renderRow(work, session, AZIMUTHS, errors, framing)
                    ImageIO.write(posed, "png", File(out, "$subject-${shape.name}$suffix-posed.png"))
                }
                println("spike: $subject ${shape.name} areas=${garment.areas.size} branches=${garment.branches.map { "${it.part.value} ${"%.3f".format(it.from)}..${"%.3f".format(it.to)} v ${"%.3f".format(it.fromV)}..${"%.3f".format(it.toV)}" }}")
            }
        }
    }
}

private const val LABEL = 40

private fun renderExamples(out: File, examples: List<String>, root: File, errors: MutableList<String>) {
    out.mkdirs()
    for (example in examples) TestWork().use { work ->
        val session = CharacterSession(exampleDocument(example, false, File(root, "examples")), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        work.settle(session)
        for (outfit in session.document.value.character.wardrobe.outfits) {
            session.send(dev.chieppa.squash.document.ShowOutfit(outfit.id))
            val (image, _) = renderRow(work, session, EXAMPLE_AZIMUTHS, errors)
            ImageIO.write(image, "png", File(out, "$example-${outfit.id.value}.png"))
        }
    }
}
