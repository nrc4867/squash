package dev.chieppa.squash.screens.pose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.Rig

/** A handle of the Pose stage a press can take. */
sealed interface PoseGrip {
    /** The ring at the body's middle, which moves the whole body. */
    data object Body : PoseGrip

    /** A limb's end, by its paw. */
    data class End(val limb: PartId) : PoseGrip

    /** A bent limb's pole target, by its paw. */
    data class Pole(val limb: PartId) : PoseGrip

    /** A tail's curl arc, by its part. */
    data class Curl(val tail: PartId) : PoseGrip

    /** The point the head looks at. */
    data object Look : PoseGrip

    /** One of the picked bone's rings. */
    data class Ring(val axis: TurnAxis) : PoseGrip
}

/** The three ways the Turn tool's rings turn a bone. */
enum class TurnAxis { TIP, TURN, LEAN }

/**
 * What the Pose stage remembers: Drag's Firm and Stretch, the limb picked, the bone the Turn tool picked with the chain Shift added to
 * it, and the handle under the pointer or held. None of it is saved or undone.
 */
@Stable
class PoseEditState {
    var firm by mutableStateOf(false)
    var stretch by mutableStateOf(false)
    var limb by mutableStateOf<PartId?>(null)
    var bone by mutableStateOf<BoneName?>(null)
    var chain by mutableStateOf<List<BoneName>>(emptyList())
    var hovered by mutableStateOf<PoseGrip?>(null)
    var held by mutableStateOf<PoseGrip?>(null)

    private var solved: PoseSolver? = null

    /** The solver over [rig], made once for each rig. */
    fun solverFor(rig: Rig): PoseSolver = solved?.takeIf { it.rig === rig } ?: PoseSolver(rig).also { solved = it }

    /** The handle drawn lit: the one held, or else the one under the pointer. */
    val lit: PoseGrip? get() = held ?: hovered

    /** Picks [bone] alone, or adds it to the chain when [adding]. */
    fun pick(bone: BoneName, adding: Boolean) {
        chain = if (adding && this.bone != null) (chain.ifEmpty { listOfNotNull(this.bone) } + bone).distinct() else emptyList()
        this.bone = bone
    }

    fun clear() {
        limb = null
        bone = null
        chain = emptyList()
        hovered = null
        held = null
        solved = null
    }
}
