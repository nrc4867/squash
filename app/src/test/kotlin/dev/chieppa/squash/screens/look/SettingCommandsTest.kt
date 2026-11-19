package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.CloseUps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What `set=part:name:value` sends: a parameter, a mark group's control, or nothing with a warning naming what the part has. */
class SettingCommandsTest {
    private val recipe = """
        {"format": 1, "id": "toy", "name": "Toy", "parts": [
          {"id": "body", "template": "tube", "role": "body", "rest": "up", "sockets": [{"n": 1, "u": 1, "v": 0}]},
          {"id": "head", "template": "head-round", "on": "body", "socket": "socket.1",
           "marks": [{"id": "lip", "points": [[0.26, 0.95], [0.25, 0.0], [0.26, 0.05]]}],
           "groups": [{"id": "mouth", "name": "Mouth", "controls": {"open": 0}, "roles": [{"role": "lipLine", "marks": ["lip"]}]}]}
        ]}
    """.trimIndent()

    private fun <T> withSession(check: (CharacterSession) -> T): T = TestWork().use { work ->
        val document = RecipeBuilder(TemplateFolder.shipped()).build(RecipeJson.read(recipe)).document
        check(CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher))
    }

    @Test
    fun aGroupControlByGroupAndNameOrBareOnTheOneMouth() = withSession { session ->
        for (name in listOf("mouth.open", "open")) {
            val command = settingCommand(session, "head", name, 1f) { error(it) } as SetMarkControls
            assertEquals(MarkGroupId("mouth"), command.id)
            assertEquals(1f, command.controls.open)
        }
    }

    @Test
    fun aTemplateOrDottedParameterIsSet() = withSession { session ->
        assertEquals(SetParameter(PartId("head"), "size", 1.2f), settingCommand(session, "head", "size", 1.2f) { error(it) })
        assertEquals(SetParameter(PartId("head"), "move.lean", 10f), settingCommand(session, "head", "move.lean", 10f) { error(it) })
    }

    @Test
    fun anUnknownNameOrPartWarnsAndSendsNothing() = withSession { session ->
        val warnings = mutableListOf<String>()
        assertNull(settingCommand(session, "head", "grin", 1f, warnings::add))
        assertNull(settingCommand(session, "head", "mouth.grin", 1f, warnings::add))
        assertNull(settingCommand(session, "tail", "size", 1f, warnings::add))
        assertEquals(3, warnings.size)
        assertTrue("mouth.open" in warnings[0] && "size" in warnings[0], warnings[0])
    }
}
