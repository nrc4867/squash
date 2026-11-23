package dev.chieppa.squash.rig

import dev.chieppa.squash.character.keys.KeyedFields
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A key scaling the blend of a part with none of its own scales the blend the build gives its join. Builds the fennec's field, so integration. */
@Tag("integration")
class KeyedBlendTest {
    @Test
    fun aPartWithNoBlendOfItsOwnScalesItsBuiltOne() {
        val character = savedFennec().character
        val leg = PartId("front-leg-l")
        assertNull(character.part(leg)?.blend, "the leg joins by the blend the build gives it")
        val fields = KeyedFields(character, shippedLibrary)
        fun blendOf(field: CharacterField) =
            field.field.members[field.runs.indexOfFirst { it.part.id == leg && it.first }].blend
        val built = blendOf(checkNotNull(fields.base))
        assertTrue(built > 0f)
        assertEquals(built * 1.5f, blendOf(checkNotNull(fields.of(listOf(PartChange(leg, blend = 1.5f))))), built * 1e-3f)
    }
}
