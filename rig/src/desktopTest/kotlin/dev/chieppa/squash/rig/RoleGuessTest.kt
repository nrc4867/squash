package dev.chieppa.squash.rig

import dev.chieppa.squash.character.catalog.guessRole
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.role.PartRoles
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoleGuessTest {
    private val folder = TemplateFolder.shipped()
    private val character = savedFennec().character

    @Test
    fun theGuessForEachFennecPartIsTheRoleItsTemplateGaveIt() {
        val guessed = character.parts.associate { it.id.value to guessRole(character, it, folder) }
        val stored = character.parts.associate { it.id.value to it.role }
        println("guessed: $guessed")
        assertEquals(stored, guessed)
        assertEquals(Role.ARM, guessed["front-leg-l"], "a leg on the front half of a four-legged body is its arm")
        assertEquals(Role.HAND, guessed["front-paw-r"])
        assertEquals(Role.FOOT, guessed["back-paw-l"])
    }

    @Test
    fun aTypedTemplateGivesItsRoleWithTheFrontLimbsAsArmsAndHands() {
        val roles = folder.roles(character)
        fun placed(type: String, on: String, socket: String) = roles.placed(checkNotNull(folder.template(TemplateId(type))), SocketRef(PartId(on), socket))
        assertEquals(Role.ARM, placed("leg", "torso", "front-leg"))
        assertEquals(Role.LEG, placed("leg", "torso", "back-leg"))
        assertEquals(Role.HAND, placed("paw", "front-leg-l", "paw"))
        assertEquals(Role.FOOT, placed("paw", "back-leg-r", "paw"))
        assertEquals(Role.TAIL, placed("tail", "torso", "tail"))
        assertTrue(roles.fourLegged, "front limbs that bear weight make the fennec four-legged")
    }

    @Test
    fun aPartWhoseTemplateGivesNoRoleIsGuessedFromWhereItSits() {
        val generic = folder.parts.associate { it.ref to it.copy(role = null) }
        val roles = PartRoles(character, { generic[it] }, folder.plans.first())
        fun placed(type: String, on: String, socket: String) = roles.placed(checkNotNull(folder.template(TemplateId(type))).copy(role = null), SocketRef(PartId(on), socket))
        assertEquals(Role.TAIL, placed("tail", "torso", "tail"), "a part leaving the rear end along the body")
        assertEquals(Role.LEG, placed("leg", "torso", "back-leg"), "a part hanging from the back half")
        assertEquals(Role.ARM, placed("leg", "torso", "front-leg"), "a part hanging from the front half")
        assertEquals(Role.HEAD, placed("head", "torso", "neck"), "a part leaving the front end along the body")
        assertEquals(Role.EAR, placed("ear", "head", "ear"), "a part on the head's top")
        assertEquals(Role.OTHER, placed("tail", "torso", "ruff"), "a part on the chest's underside fits nothing")
    }
}
