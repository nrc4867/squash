package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterFactoryTest {
    @Test
    fun aNewCharacterIsEmptyAndItsCommandsPlaceThePlansRoot() {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        assertEquals("Fennec", new.document.character.name)
        assertEquals(caninePlan.ref, new.document.character.bodyPlan)
        assertTrue(new.document.character.parts.isEmpty())
        val add = new.commands.single() as AddPart
        assertEquals(new.root, add.part.id)
        assertEquals(TemplateId("torso"), add.part.template.id)
        assertNull(add.part.socket)
    }

    @Test
    fun aPlanWhoseRootIsNotLoadedIsRefused() {
        val missing = caninePlan.copy(root = caninePlan.root.copy(template = TemplateId("wing")))
        assertFailsWith<IllegalArgumentException> { newCharacter(missing, shipped, "Fennec") }
    }

    @Test
    fun templatesListInTheOrderThePlanReachesThem() {
        assertEquals(listOf("torso", "head", "ear", "leg", "paw", "tail"), caninePlan.templateOrder(shipped).map { it.id.value })
    }

    @Test
    fun partsAndChipsCountWhatThePlanAndTemplatesHold() {
        // Torso, head, ears, two legs and their paws, tail; the ruff has no loaded template.
        assertEquals(8, caninePlan.partCount(shipped))
        val chips = caninePlan.chips(shipped).associate { it.name to it.count }
        assertEquals(mapOf("Torso" to 1, "Head" to 1, "Leg" to 4, "Tail" to 1), chips)
    }
}
