package dev.chieppa.squash.screens.shell

/** What the right drawer holds for a selected part. */
enum class PartDrawerKind { PART, OUTLINE, OUTLINE_CURVES }

/**
 * The boards' one right drawer, by the armed tool: the part's template with no tool armed; Outline curves once the part
 * has an outline, under Oversketch and Edit curves alike; Outline while Draw outline waits for a part's first stroke; Part
 * otherwise.
 */
fun partDrawerKind(tool: BuildTool, hasPart: Boolean, drawn: Boolean): PartDrawerKind = when {
    tool == BuildTool.NONE -> PartDrawerKind.PART
    drawn -> PartDrawerKind.OUTLINE_CURVES
    hasPart && tool == BuildTool.DRAW_OUTLINE -> PartDrawerKind.OUTLINE
    else -> PartDrawerKind.PART
}
