package dev.chieppa.squash.schemagen

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.schemagen.reference.Reference
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The recipe reference under docs/reference/ is what the code generates, and says what every newest template's parameters do. */
class ReferenceTest {
    private val folder = Path.of(System.getProperty("squash.reference"))

    @Test
    fun theCommittedReferenceIsCurrent() {
        for ((name, text) in Reference.render()) {
            val file = folder.resolve(name)
            assertTrue(file.exists(), "docs/reference/$name is missing; run generateSchemas")
            assertEquals(text, file.readText().replace("\r\n", "\n"), "docs/reference/$name is stale; run generateSchemas")
        }
    }

    @Test
    fun everyNewestTemplateParameterSaysWhatItDoes() {
        val newest = TemplateFolder.shipped().parts.groupBy { it.id }.values.map { all -> all.maxBy { it.version } }
        val undocumented = newest.flatMap { template -> template.params.filterValues { it.doc.isNullOrBlank() }.keys.map { "${template.id.value} ${template.version}: $it" } }
        assertEquals(emptyList(), undocumented)
    }
}
