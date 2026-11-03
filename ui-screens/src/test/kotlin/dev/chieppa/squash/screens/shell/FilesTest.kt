package dev.chieppa.squash.screens.shell

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

class FilesTest {
    private val format = ScratchFormat()

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
        FileStorage.write(file.absolutePathString(), format.encode(new.document))
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
