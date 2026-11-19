package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class KeptSessionTest {
    private val templates = TemplateFolder.shipped()
    private val kept = KeptSession()
    private val fennec = File(File(checkNotNull(System.getProperty("squash.templates"))).parentFile, "recipes/fennec.recipe.json").readText()

    private fun build(text: String) = RecipeBuilder(templates).build(RecipeJson.read(text))

    @AfterTest
    fun close() = kept.close()

    @Test
    fun `an edited recipe reuses the session, which shows the new document`() {
        val first = kept.open(build(fennec))
        val edited = build(fennec.replaceFirst("\"size\": 1.12", "\"size\": 1.2"))
        val second = kept.open(edited)
        assertSame(first, second)
        assertEquals(edited.document, second.session.document.value)
    }

    @Test
    fun `a recipe of another character gets a session of its own`() {
        val first = kept.open(build(fennec))
        val other = build(fennec.replaceFirst("\"id\": \"fennec\"", "\"id\": \"fennec-two\""))
        val second = kept.open(other)
        assertNotSame(first, second)
        assertEquals(other.document, second.session.document.value)
    }
}
