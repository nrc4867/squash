package dev.chieppa.squash.screens.look.recipe

import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileChangesTest {
    @TempDir
    lateinit var folder: File

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `a write to the file is reported and one to another file is not`() {
        val file = File(folder, "a.recipe.json").apply { writeText("{}") }
        FileChanges(file).use { changes ->
            File(folder, "other.json").writeText("{}")
            file.writeText("{\"id\": 1}")
            assertTrue(changes.next())
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun `a closed watch stops`() {
        val file = File(folder, "a.recipe.json").apply { writeText("{}") }
        val changes = FileChanges(file)
        changes.close()
        assertFalse(changes.next())
    }
}
