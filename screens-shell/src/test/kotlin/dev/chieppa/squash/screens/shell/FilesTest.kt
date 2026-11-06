package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.BoardPlace
import dev.chieppa.squash.document.ImageBytes
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.SetReferenceImage
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.stretch.document.FileStorage
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class FilesTest {
    private val format = SquashFormat()

    @Test
    fun aScratchSaveReadsBackTheSameDocument() {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        val document = new.commands.fold(new.document) { d, c -> c.apply(d).document }
        assertEquals(document, format.decode(format.encode(document)))
    }

    @Test
    fun aScratchSaveKeepsAPairsSharedOutline() {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        val rooted = new.commands.fold(new.document) { d, c -> c.apply(d).document }
        val socket = openSockets(rooted, caninePlan, shipped).first { it.name == "front-leg" }
        val legs = placeOnSocket(socket, assertNotNull(shipped.template(TemplateId("leg"))), rooted)
        val curve = OutlineCurve(List(4) { CurvePoint(it.toFloat(), 0f) })
        val document = SetOutline(legs.first().id, View.SIDE, curve).apply(AddParts(legs).apply(rooted).document).document
        val read = format.decode(format.encode(document))
        assertEquals(document, read)
        assertEquals(curve, read.character.resolved(legs.last().id)?.outlines?.get(View.SIDE))
    }

    @Test
    fun aSaveIsAZipWithItsImagesAsFilesOfTheirOwn() {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 9, 9)
        val image = ReferenceImage(ImageId("sheet"), "Side sheet", ImageBytes(png), 64, 48, BoardPlace(0f, 0f, 120f))
        val document = SetReferenceImage(image.id, image).apply(new.commands.fold(new.document) { d, c -> c.apply(d).document }).document
        val bytes = format.encode(document)
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertEquals(listOf("character.json", "images/sheet.png"), names)
        assertEquals(document, format.decode(bytes))
    }

    @Test
    fun aPlainJsonSaveStillReads() {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        val document = new.commands.fold(new.document) { d, c -> c.apply(d).document }
        assertEquals(document, format.decode(format.plainText(document).encodeToByteArray()))
    }

    @Test
    fun aFileThatIsNotASaveIsRefused() {
        assertFailsWith<Exception> { format.decode("not json".encodeToByteArray()) }
    }

    @Test
    fun recentFilesPersistOnePathALine() {
        val store = RecentStore(Files.createTempDirectory("squash").resolve("nested").resolve("recent.txt"))
        assertEquals(emptyList(), store.load())
        store.save(listOf("a.json", "b.json"))
        assertEquals(listOf("a.json", "b.json"), store.load())
    }

    @Test
    fun aRecentProjectReadsItsCharacter() {
        val file = Files.createTempFile("fennec", ".json")
        val new = newCharacter(caninePlan, shipped, "Fennec")
        runBlocking { FileStorage.write(file.absolutePathString(), format.encode(new.document)) }
        val project = assertNotNull(readRecentProject(file.absolutePathString(), format, shipped))
        assertEquals("Fennec", project.name)
        assertEquals(8, project.progress.total)
        file.writeBytes("broken".encodeToByteArray())
        assertNull(readRecentProject(file.absolutePathString(), format, shipped))
    }

    @Test
    fun aStampIsATimeTodayAndADateOtherwise() {
        val now = Instant.parse("2026-11-03T14:20:00Z")
        assertEquals("<14:20>", recentStamp(now, now, ZoneOffset.UTC) { "<$it>" })
        assertEquals("Oct 28", recentStamp(Instant.parse("2026-10-28T09:00:00Z"), now, ZoneOffset.UTC) { it })
    }
}
