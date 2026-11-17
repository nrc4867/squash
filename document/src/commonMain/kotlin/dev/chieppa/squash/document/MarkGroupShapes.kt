package dev.chieppa.squash.document

private val MOUTH_ORDER = listOf(MarkRole.INNER, MarkRole.TONGUE, MarkRole.TEETH, MarkRole.LIP_LINE, MarkRole.CORNERS)

/** A mouth with no marks yet, closed: its roles in their fixed draw order. */
fun mouthGroup(id: MarkGroupId, name: String): MarkGroup =
    MarkGroup(id, name, MOUTH_ORDER.map { GroupRole(it) }, controls = MarkControls(open = 0f))

/** Whether this group is a mouth, by its lip line. */
val MarkGroup.isMouth: Boolean get() = roles.any { it.role == MarkRole.LIP_LINE }
