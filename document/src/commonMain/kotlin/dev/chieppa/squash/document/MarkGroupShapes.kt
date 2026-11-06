package dev.chieppa.squash.document

private val EYE_ORDER = listOf(
    MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL, MarkRole.HIGHLIGHT, MarkRole.LID, MarkRole.LASHES, MarkRole.BROW,
)
private val LID_CLIPS = listOf(MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL)
private val MOUTH_ORDER = listOf(MarkRole.INNER, MarkRole.TONGUE, MarkRole.TEETH, MarkRole.LIP_LINE, MarkRole.CORNERS)

/** An eye with no marks yet: its roles in their fixed draw order, the lid clipping the white, iris and pupil. */
fun eyeGroup(id: MarkGroupId, name: String): MarkGroup =
    MarkGroup(id, name, EYE_ORDER.map { GroupRole(it) }, LID_CLIPS.map { RoleClip(MarkRole.LID, it) })

/** A mouth with no marks yet, closed: its roles in their fixed draw order. */
fun mouthGroup(id: MarkGroupId, name: String): MarkGroup =
    MarkGroup(id, name, MOUTH_ORDER.map { GroupRole(it) }, controls = MarkControls(open = 0f))

/** Whether this group is an eye, by its lid. */
val MarkGroup.isEye: Boolean get() = roles.any { it.role == MarkRole.LID }

/** Whether this group is a mouth, by its lip line. */
val MarkGroup.isMouth: Boolean get() = roles.any { it.role == MarkRole.LIP_LINE }
