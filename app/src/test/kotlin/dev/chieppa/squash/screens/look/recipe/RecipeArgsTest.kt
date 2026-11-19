package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.screens.look.LookArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeArgsTest {
    private fun look(vararg args: String) = RecipeArgs(arrayOf(*args)).let { LookArgs(it.lookArgs("dressed")) }

    @Test
    fun `a build renders the back and top views and the usual close-ups by default`() {
        val look = look("recipe=a.json")
        assertTrue(look.around)
        assertEquals(setOf("face", "hand", "foot"), look.close)
        assertEquals(emptyList(), look.closeParts)
        assertEquals(listOf("dressed"), look.outfits)
        assertNull(look.show)
    }

    @Test
    fun `close takes part ids beside its words`() {
        val look = look("recipe=a.json", "close=foot-l,hand,head")
        assertEquals(listOf("foot-l", "head"), look.closeParts)
        assertTrue("hand" in look.close)
    }

    @Test
    fun `only draws those parts and skips the measurements unless asked`() {
        val args = RecipeArgs(arrayOf("recipe=a.json", "only=ear-l,head"))
        assertEquals(setOf("ear-l", "head"), LookArgs(args.lookArgs(null)).show)
        assertFalse(args.measure)
        assertTrue(RecipeArgs(arrayOf("recipe=a.json", "only=head", "measure=true")).measure)
    }

    @Test
    fun `around false leaves out the back and top views`() {
        assertFalse(look("recipe=a.json", "around=false").around)
    }

    @Test
    fun `watch, crop and fit are read`() {
        val args = RecipeArgs(arrayOf("root=/r", "recipe=a.json", "watch=true", "fit=true", "crop=sheet.png"))
        assertTrue(args.watch)
        assertTrue(args.fit)
        assertEquals("sheet.png", args.crop?.name)
    }
}
