package dev.chieppa.squash.screens.look.recipe.engine

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class EngineSnapshotTest {
    @TempDir
    lateinit var dir: Path

    private val engines get() = dir.resolve("app/build/recipe-engine")
    private val pointer get() = dir.resolve("app/build/recipe-launcher/engine.txt")
    private val caches get() = dir.resolve("gradle-home/caches")

    private fun snapshot() = EngineSnapshot(engines, pointer, listOf(caches))

    private fun classes(text: String): Path = dir.resolve("classes").apply {
        resolve("dev/fox").createDirectories()
        resolve("dev/fox/Fox.class").writeText(text)
    }

    private fun jar(name: String, time: Long, text: String): Path = dir.resolve("libs/$name").also { path ->
        path.parent.createDirectories()
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("dev/dog/Dog.class").apply { this.time = time })
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }

    private fun cached(): Path = caches.resolve("modules/ab12/lib.jar").also { it.parent.createDirectories(); it.writeText("cached") }

    private fun take(vararg entries: Path) = snapshot().take(entries.toList(), "java") { folder, classpath -> listOf(folder.toString()) + classpath.map { it.toString() } }

    @Test
    fun `a snapshot copies the classes into a folder of their own, points at it and starts from it`() {
        val cache = cached()
        val version = take(classes("fox"), jar("dog.jar", 1_000, "dog"), cache)
        val folder = engines.resolve(version)
        assertEquals(version, snapshot().current())
        assertEquals("fox", folder.resolve("lib/0-classes/dev/fox/Fox.class").readText())
        val args = folder.resolve(ARGS_FILE).readText().lines()
        assertEquals(folder.toString(), args[0])
        assertTrue(args[1].startsWith(folder.toString()) && args[2].startsWith(folder.toString()))
        assertEquals(cache.toString(), args[3])
        assertTrue(folder.resolve(LEASE_FILE).exists())
        assertEquals("java", folder.resolve(JAVA_FILE).readText().trim())
    }

    @Test
    fun `the same classes give the same version, whatever the jar's timestamps`() {
        val first = take(classes("fox"), jar("dog.jar", 1_000_000, "dog"))
        val second = take(classes("fox"), jar("dog.jar", 9_000_000, "dog"))
        assertEquals(first, second)
    }

    @Test
    fun `changed classes get a new snapshot and the old one is left whole`() {
        val classes = classes("fox")
        val old = take(classes)
        classes.resolve("dev/fox/Fox.class").writeText("fox, compiled again")
        val new = take(classes)
        assertNotEquals(old, new)
        assertEquals(new, snapshot().current())
        assertEquals("fox", engines.resolve(old).resolve("lib/0-classes/dev/fox/Fox.class").readText())
        assertEquals("fox, compiled again", engines.resolve(new).resolve("lib/0-classes/dev/fox/Fox.class").readText())
    }

    @Test
    fun `a prune keeps the newest and the current snapshots and any a JVM holds`() {
        val versions = (1..5).map { take(classes("fox $it")) }
        versions.forEachIndexed { i, version -> Files.setLastModifiedTime(engines.resolve(version), FileTime.fromMillis(1_000L * (i + 1))) }
        snapshot().point(versions[0])
        FileChannel.open(engines.resolve(versions[1]).resolve(LEASE_FILE), READ).use { channel ->
            channel.lock(0, Long.MAX_VALUE, true).use {
                val pruned = snapshot().prune(kept = 2)
                assertEquals(listOf(versions[2]), pruned)
            }
        }
        assertTrue(engines.resolve(versions[0]).exists(), "the current one")
        assertTrue(engines.resolve(versions[1]).resolve(ARGS_FILE).exists(), "the leased one")
        assertTrue(engines.resolve(versions[3]).exists() && engines.resolve(versions[4]).exists(), "the newest two")
    }

    @Test
    fun `a snapshot left partial is cleared by the next prune`() {
        take(classes("fox"))
        val partial = engines.resolve(".partial-123").also { it.resolve("lib").createDirectories() }
        snapshot().prune()
        assertTrue(!partial.exists())
    }
}
