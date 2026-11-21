package dev.chieppa.squash.screens.look.recipe.server

import dev.chieppa.squash.screens.look.recipe.ThreadOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class BuildLogTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `lines are kept and sent as printed, and the files printed that exist are read from them`() {
        val sent = mutableListOf<String>()
        val log = BuildLog { stream, line -> sent += "${stream.key} $line" }
        val written = dir.resolve("fox.json").createFile().toString()
        log.stream(LogStream.OUT).apply {
            println("character: $written")
            print("renders: ${dir.resolve("missing")} (12 ms)\r\n")
        }
        log.stream(LogStream.ERR).println("recipe warning at $.parts")
        assertEquals(listOf("out character: $written", "out renders: ${dir.resolve("missing")} (12 ms)", "err recipe warning at $.parts"), sent)
        assertEquals(listOf(written), log.files())
        assertEquals(emptyList(), log.files(from = 1))
    }

    @Test
    fun `a send that fails closes the log, which keeps the lines`() {
        val log = BuildLog { _, _ -> error("the client went away") }
        log.add(LogStream.OUT, "one")
        assertFalse(log.isOpen)
        log.add(LogStream.OUT, "two")
        assertEquals(2, log.size)
    }

    @Test
    fun `two threads print only into their own streams`() {
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()
        val other = thread { ThreadOutput.into(PrintStream(second, true)) { println("dog") } }
        ThreadOutput.into(PrintStream(first, true)) { println("fox") }
        other.join()
        assertEquals("fox", first.toString().trim())
        assertEquals("dog", second.toString().trim())
    }
}
