package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetOneSurface
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetSocketBlend

/** How wide the blend under her jaw is, so her chin stays a chin over the neck. */
private const val JAW_BLEND = 0.015f

/** How wide the blend where her hair meets her head is, so the hairline stays a line and the face's paint stays the head's. */
private const val HAIRLINE_BLEND = 0.012f

/**
 * The commands that build her as one surface: the switch, a narrow blend under the jaw and where the crown of her hair meets her
 * head, her body's joins unlined and her hairline inked, as her drawing has them, and her painted eyes.
 */
fun womanOneSurface(): List<DocumentCommand> = listOf(SetOneSurface(true), SetSocketBlend(HEAD, JAW_BLEND)) +
    SetSocketBlend(HAIR, HAIRLINE_BLEND) +
    listOf(HEAD, NECK, ARM_L, HAND_L, LEG_L, FOOT_L).map { SetParameter(it, JoinParams.INK, 0f) } +
    womanPaintedEyes()
