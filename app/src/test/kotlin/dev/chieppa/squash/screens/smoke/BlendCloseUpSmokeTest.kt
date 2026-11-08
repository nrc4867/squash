package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.block.Blend
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.person.EMPTY_PERSON
import dev.chieppa.squash.screens.person.HAND_L
import dev.chieppa.squash.screens.person.personCommands
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val CURL = 0.7f

/** The digits into the [palm] run, which keeps its place: not one digit into the next, nor into the webs, already fillets. */
private fun intoThe(palm: String, digits: String) = listOf(listOf(palm, digits))

/** The test-only variants: the shipped hand, paw and leg with a blend block switched on, each under its own ref. */
internal fun blended(template: PartTemplate): PartTemplate = when (template.id.value to template.version) {
    "hand" to 3 -> template.copy(blocks = template.blocks + Blend(radius = Param.of(0.12f), between = intoThe("palm", "fingers"), still = listOf("palm")))
    "paw" to 3 -> template.copy(blocks = template.blocks + Blend(radius = Param.of(0.12f), between = intoThe("body", "toes"), still = listOf("body")))
    "leg" to 1 -> template.copy(blocks = template.blocks + Blend(radius = Param.of(0.2f), between = emptyList(), socket = true))
    else -> template
}

/**
 * The person's hand, the fennec's front paw and the fennec's front leg where it leaves the torso, with the implicit blend off (the
 * shipped templates) and on (test-only variants with a blend block), left for a person to compare.
 */
/** The shipped templates, or with [on] the test-only variants with a blend block in place of the hand, paw and leg. */
internal fun blendLibrary(on: Boolean): TemplateLibrary {
    val folder = TemplateFolder.shipped()
    return TemplateLibrary(if (on) folder.parts.map(::blended) else folder.parts, folder.plans)
}

@Tag("slow")
class BlendCloseUpSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()
    private val closeUps = CloseUps(work, errors)

    private fun compare(empty: SquashDocument, commands: List<DocumentCommand>, part: PartId, name: String, curl: Boolean) {
        for (on in listOf(false, true)) {
            val session = closeUps.session(empty, commands, blendLibrary(on))
            val state = if (on) "on" else "off"
            closeUps.keep(session, part, "blend-$name-rest-$state")
            if (curl) {
                val pose = SessionPose(session)
                work.settle(session)
                val rig = assertNotNull(pose.rig.rig.output.value)
                session.send(SetPose(rig.curled(Pose(), listOf(part), CURL)))
                closeUps.keep(session, part, "blend-$name-curled-$state")
            }
        }
        assertEquals(emptyList(), errors)
    }

    @Test
    fun thePersonsHand() = compare(EMPTY_PERSON, personCommands(), HAND_L, "hand", curl = true)

    @Test
    fun theFennecsPaw() = compare(EMPTY_FENNEC, fennecCommands(), FRONT_PAW_L, "paw", curl = true)

    @Test
    fun theFennecsLegUnderItsTorso() = compare(EMPTY_FENNEC, fennecCommands(), FRONT_LEG_L, "leg", curl = false)
}
