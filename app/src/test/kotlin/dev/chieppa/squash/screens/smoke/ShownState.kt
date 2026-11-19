package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.surface.CharacterState
import kotlin.test.assertNotNull

/** What the session shows, settled: its posed body, color, surface work and lines, without fur, and its one surface posed with its paint and dress. */
fun CharacterSession.shownState(): CharacterState {
    val posed = sessionSurface.posed.value?.output?.value?.posed
    return CharacterState(
        assertNotNull(posedBody.output.value),
        assertNotNull(bodyColor.output.value),
        assertNotNull(posedSurfaceWork.output.value),
        document.value.character.palette,
        assertNotNull(posedLines.output.value),
        fur = FurView.EMPTY,
        surface = posed?.surface ?: assertNotNull(characterSurface.output.value?.surface),
        paint = posed?.paint ?: surfacePaint.output.value?.paint,
        dress = posed?.dress ?: sessionSurface.dress.output.value?.dress,
        creases = posed?.creases,
    )
}
