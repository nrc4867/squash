package dev.chieppa.squash.character

import dev.chieppa.squash.character.load.TemplateFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class PackedTemplatesTest {
    @Test
    fun theResourcesHoldTheSameTemplatesAsTheFolder() {
        assertEquals(shipped, TemplateFolder.shipped())
    }
}
