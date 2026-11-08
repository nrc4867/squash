package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.chips
import dev.chieppa.squash.character.catalog.firstTaken
import dev.chieppa.squash.character.catalog.isFinned
import dev.chieppa.squash.character.catalog.isWinged
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.partCount
import dev.chieppa.squash.character.lint.BodyPlanLint
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The fin template and the fish plan: a tube body with a dorsal fin, a pectoral pair, an anal fin, a tail and two eyes, before anything is drawn. */
class FishPlanTest {
    private val plan = shipped.plans.single { it.id.value == "fish" }

    @Test
    fun theFinIsTheSheetWithTheFinRoleAndLintsClean() {
        val fin = template("fin")
        val sheet = template("sheet")
        assertEquals(emptyList(), TemplateLint.lint(fin))
        assertEquals(Role.FIN, fin.role)
        assertTrue(fin.isSheet)
        assertEquals(sheet.copy(id = fin.id, name = fin.name, role = fin.role), fin, "a fin is the generic sheet with its role named")
        assertEquals(1, fin.blocks.sumOf { it.joints().size }, "one bone end to end")
        assertEquals(sheet.topology, fin.topology)
        assertFalse(PartBuilder.build(fin).single().closedV)
    }

    @Test
    fun thePlanLintsCleanOverTheGenericTubeAndItsFins() {
        assertEquals(emptyList(), BodyPlanLint.lint(plan, shipped.parts))
        assertEquals("tube", plan.root.template.value)
        assertEquals(template("tube").axis.rest, plan.root.rest)
        assertEquals(listOf("dorsal", "tail", "pectoral", "anal", "eye"), socketsOn(plan, template("tube"), emptyMap()).map { it.name })
        assertTrue(plan.isFinned(shipped))
        assertFalse(plan.isWinged(shipped))
    }

    @Test
    fun newCharacterListsTheFishAfterTheBipedAndStartsOnAnUndrawnBody() {
        assertEquals(listOf("canine-quadruped", "bird", "biped", "fish", "blank"), shipped.plans.map { it.id.value })
        var next = 0
        val made = newCharacter(plan, shipped, "Koi") { "id-${next++}" }
        val document = made.commands.fold(made.document) { document, command -> command.apply(document).document }
        val body = document.character.parts.single()
        assertEquals("tube", body.template.id.value)
        assertEquals(Role.BODY, body.role)
        assertEquals(listOf("scale", "belly", "patch", "fin", "eye", "pupil"), document.character.palette.slots.map { it.name.value })
        val sockets = socketsOn(plan, template("tube"), emptyMap())
        assertEquals(setOf("pectoral", "eye"), sockets.filter { it.pair != null }.map { it.name }.toSet())
        assertEquals("fin", assertNotNull(shipped.firstTaken(sockets.first())).id.value)
        assertEquals(6, plan.partCount(shipped), "a body, a dorsal, a tail, a pectoral, an anal and an eye, a pair counting once")
        assertEquals(mapOf("Body" to 1, "Fin" to 4, "Sheet" to 1, "Blob" to 2), plan.chips(shipped).associate { it.name to it.count })
    }
}
