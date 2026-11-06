package dev.chieppa.squash.screens.shell

/** What the Shape inspector holds first for a selected part. */
enum class PartDrawerKind { PART, OUTLINE, OUTLINE_CURVES, SPINE_WIDTH, SMOOTH, MOVE }

/**
 * The Shape inspector's first sections, by the armed tool: the part's fit with no tool armed; Outline curves once the part
 * has an outline, under Oversketch and Edit curves alike; Outline while Draw outline waits for a part's first stroke; Part
 * otherwise. Spine and width has its own whatever the part holds, since it writes the outlines afresh, and so have Smooth
 * and Move.
 */
fun partDrawerKind(tool: BuildTool, hasPart: Boolean, drawn: Boolean): PartDrawerKind = when {
    tool == BuildTool.NONE || tool.paints || tool.inks -> PartDrawerKind.PART
    hasPart && tool == BuildTool.SPINE_WIDTH -> PartDrawerKind.SPINE_WIDTH
    hasPart && tool == BuildTool.SMOOTH -> PartDrawerKind.SMOOTH
    hasPart && tool == BuildTool.MOVE -> PartDrawerKind.MOVE
    drawn -> PartDrawerKind.OUTLINE_CURVES
    hasPart && tool == BuildTool.DRAW_OUTLINE -> PartDrawerKind.OUTLINE
    else -> PartDrawerKind.PART
}
