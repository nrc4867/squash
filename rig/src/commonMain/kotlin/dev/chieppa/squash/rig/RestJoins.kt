package dev.chieppa.squash.rig

/**
 * Each corner's joins as the rest field meshed them, [MOST_MEMBERS] slots a corner in the order of its members' set: the share of its
 * blend the rest field gives each member's join at the corner's rest place, [shares], and the share [posedBlendShare] gives the angle
 * there, [operators]; written by [PosedFieldReader.readRest].
 */
internal class RestJoins(corners: Int) {
    val shares = FloatArray(corners * MOST_MEMBERS) { 1f }
    val operators = FloatArray(corners * MOST_MEMBERS) { 1f }
}
