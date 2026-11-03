package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class CharacterSessionTest {
    private val document = SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1)))

    @Test
    fun aPartKeepsOneNode() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val session = CharacterSession(document, TemplateLibrary(emptyList()), backgroundScope, dispatcher)
        assertSame(session.patches(PartId("a")), session.patches(PartId("a")))
    }
}
