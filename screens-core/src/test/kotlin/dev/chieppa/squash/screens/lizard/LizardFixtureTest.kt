package dev.chieppa.squash.screens.lizard

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.load.withSaved
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.saveAsTemplate
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$LIZARD_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved lizard stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class LizardFixtureTest {
    private val work = TestWork()

    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(lizardDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(lizardDocument(), savedLizard())
        assertEquals(lizardDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyPartIsGenericWithItsRoleChosen() {
        val parts = lizardDocument().character.parts
        assertTrue(parts.all { it.template.id.value in setOf("tube", "blob") })
        assertTrue(parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.TAIL to 1, Role.ARM to 2, Role.LEG to 2, Role.HAND to 2, Role.FOOT to 2, Role.HEAD to 1, Role.OTHER to 2),
            parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
    }

    @Test
    fun theGuessAgreesWithTheChosenRolesOfItsLimbsAndTail() {
        val character = lizardDocument().character
        val roles = shipped.roles(character)
        for (id in listOf(TAIL, FRONT_LEG_L, BACK_LEG_R, BACK_FOOT_L, HEAD)) {
            val part = assertNotNull(character.part(id))
            assertEquals(part.role, roles.guess(part), "${id.value} sits where its role says")
        }
    }

    @Test
    fun itBuildsWithTheJointsPlacedByHand() {
        val document = lizardDocument()
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        fun joints(id: PartId) = assertNotNull(assembled.part(id)).template.blocks.flatMap { it.joints() }.map { it.name }
        assertEquals(listOf("base") + (1..4).map(HandJoints::name) + HandJoints.TIP, joints(LIZARD_BODY))
        assertEquals(listOf("base", HandJoints.name(1), HandJoints.name(2), HandJoints.TIP), joints(TAIL))
        assertEquals(listOf("base", HandJoints.name(1), HandJoints.TIP), joints(BACK_LEG_R))
        assembled.parts.filter { !it.planned }.forEach { assertEquals(emptyList(), TemplateLint.lint(it.template), "${it.id?.value} lints clean as built") }
        assertTrue(assertNotNull(assembled.part(TAIL)).template.axis.rest.x < -0.99f, "the tail rests back")
    }

    @Test
    fun aLegSavesAsATemplateThatLoadsAndBuilds() {
        val folder = Files.createTempDirectory("lizard-templates")
        val state = AppState(shipped.withSaved(folder), work)
        state.open(lizardDocument())
        val saved = assertNotNull(state.saveAsTemplate(BACK_LEG_L, "lizard-leg"))
        assertEquals(TemplateId("lizard-leg"), saved.id)
        assertEquals(Role.LEG, saved.role)
        val loaded = shipped.withSaved(folder)
        val leg = loaded.parts.single { it.id == TemplateId("lizard-leg") }
        assertEquals(emptyList(), TemplateLint.lint(leg))
        assertEquals(listOf("base", "joint1", HandJoints.TIP), leg.blocks.flatMap { it.joints() }.map { it.name })
        assertEquals(listOf("socket1"), leg.blocks.flatMap { it.sockets() }.map { it.name })

        val document = AddParts(listOf(PartInstance(PartId("extra"), leg.ref, SocketRef(LIZARD_BODY, "socket.3"), role = Role.LEG))).apply(lizardDocument()).document
        val reopened = AppState(loaded, work)
        val session = reopened.open(document)
        assertNotNull(session.builtTemplate(assertNotNull(document.character.part(PartId("extra")))))
        val library = TemplateLibrary(loaded.parts, loaded.plans)
        val plan = loaded.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        assertTrue(assertNotNull(assembled.part(PartId("extra"))).surface.patches.isNotEmpty(), "a part made from it builds")
    }
}
