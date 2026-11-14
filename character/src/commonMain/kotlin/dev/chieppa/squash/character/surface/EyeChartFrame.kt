package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Vec3

/**
 * Where one side of a painted eye lies: its [middle], how far body space runs per unit of the eye's chart toward the head's front,
 * [perU], and up, [perV], the way it [faces], and whether it is the [mirrored] side.
 */
class EyeChartFrame(val middle: Vec3, val perU: Vec3, val perV: Vec3, val faces: Vec3, val mirrored: Boolean)
