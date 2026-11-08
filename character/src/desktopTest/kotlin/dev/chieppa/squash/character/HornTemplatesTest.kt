package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How near its parent's tip an antler on the tip socket starts, as a share of the parent's length. */
private const val AT_THE_TIP = 0.25f

/** `horn` leans back to a point; `antler` branches by taking antlers on its tip and its tine. */
class HornTemplatesTest {
    private val plan = shipped.plans.first()
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val horn = assertNotNull(shipped.template(TemplateId("horn")))
    private val antler = assertNotNull(shipped.template(TemplateId("antler")))

    @Test
    fun theyLintCleanAsHorns() {
        for (template in listOf(horn, antler, assertNotNull(shipped.template(TemplateId("spike"))))) {
            assertEquals(emptyList(), TemplateLint.lint(template), template.id.value)
            assertEquals(Role.HORN, template.role)
        }
        assertEquals(listOf("tip", "tine"), antler.blocks.flatMap { it.sockets() }.map { it.name })
    }

    @Test
    fun anAntlerOnAnAntlersTipCarriesItOn() {
        val new = newCharacter(plan, shipped, "Stag")
        val rooted = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val base = PartId("antler")
        val branch = PartId("antler-tip")
        val tine = PartId("antler-tine")
        val document = listOf(
            HandSockets.add(new.root, 1, 0.9f, 0f, 0f, paired = false),
            AddParts(listOf(PartInstance(base, antler.ref, SocketRef(new.root, HandSockets.name(1)), role = Role.HORN))),
            AddParts(listOf(PartInstance(branch, antler.ref, SocketRef(base, "tip"), role = Role.HORN), PartInstance(tine, antler.ref, SocketRef(base, "tine"), role = Role.HORN))),
        ).fold(rooted) { document, command -> command.apply(document).document }
        val character = document.character
        val assembled = assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library)
        val parent = assertNotNull(assembled.part(base))
        val tip = assertNotNull(parent.pointOn(1f, 0f))
        val length = antler.axis.length
        assertTrue((assertNotNull(assembled.part(branch)).origin - tip).length < AT_THE_TIP * length, "the tip's antler starts at its parent's tip")
        assertTrue(assertNotNull(assembled.part(branch)).origin.y > parent.origin.y + length / 2f, "and stands above it")
        assertTrue(assertNotNull(assembled.part(tine)).origin.y < assertNotNull(assembled.part(branch)).origin.y, "the tine leaves lower down")
    }
}
