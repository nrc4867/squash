package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.SetSlotMaterial
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.stretch.theme.StretchTheme
import dev.chieppa.stretch.theme.StretchThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** The material picker: a preset pick sets the slot's material to it alone, and a slider drag is one step writing one setting. */
@OptIn(ExperimentalTestApi::class)
class MaterialPickerTest {
    private val work = TestWork()
    private val folder = TemplateFolder.shipped()
    private val session = CharacterSession(fennecDressedDocument(), TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)

    private fun steps() = session.history.steps.value.done.size

    private fun material() = session.document.value.character.palette.material(FUR)

    @Test
    fun pickingAPresetSendsItAloneAndADragIsOneStep() = runComposeUiTest {
        setContent {
            StretchTheme(StretchThemes.Ink) {
                val document by session.document.collectAsState()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MaterialPresets(session, document.character, FUR)
                    MaterialMore(session, document.character, FUR, open = true) {}
                }
            }
        }
        val before = steps()
        onNodeWithTag(PaintTags.materialPreset("metal")).performClick()
        waitForIdle()
        assertIs<SetSlotMaterial>(session.history.steps.value.done.last())
        assertEquals(SlotMaterial(MaterialPreset.METAL), material())
        assertEquals(before + 1, steps())

        onNodeWithTag(PaintTags.MATERIAL_RIM).performScrollTo().performMouseInput {
            moveTo(Offset(width * 0.2f, height / 2f))
            press()
            moveTo(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.7f, height / 2f))
            release()
        }
        waitForIdle()
        assertEquals(before + 2, steps(), "a slider drag is one step")
        val dragged = assertNotNull(material())
        assertEquals(MaterialPreset.METAL, dragged.preset)
        assertEquals(SlotMaterial(MaterialPreset.METAL, rim = dragged.rim), dragged, "the drag writes the rim alone")
    }
}
