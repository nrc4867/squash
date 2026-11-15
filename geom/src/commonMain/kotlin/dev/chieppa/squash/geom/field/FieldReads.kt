package dev.chieppa.squash.geom.field

/**
 * Where members' own fields are read: a batch of points in, each with the member it reads, and each one's field out, with where it lies
 * on the member's run for a point asked to be placed (see [JoinedMember.place]). One implementation is chosen for a session and every
 * reader of its fields asks it, so values kept from one build and read afresh in the next come from the same reads.
 */
interface FieldReads {
    /** Reads every query of [batch] into its answers. */
    fun read(batch: FieldBatch)

    /**
     * Whether a reader gathers its points into batches before asking: false where each point is read where it is asked, on the asking
     * thread, as [CpuFieldReads] does, so a reader hands over no batch at all.
     */
    val gathers: Boolean

    /** Changes when what answers the reads changes, so values kept from earlier reads are read again. */
    val generation: Int
}

/** Reads each point on the asking thread through the members' own solids. */
object CpuFieldReads : FieldReads {
    override fun read(batch: FieldBatch) {
        val out = FloatArray(PLACE_ARRAY)
        for (i in 0 until batch.size) {
            val member = batch.members[batch.member[i]]
            val p = batch.point(i)
            if (batch.place[i]) {
                member.place(p, out)
                batch.field[i] = out[PLACE_FIELD]
                batch.u[i] = out[PLACE_U]
                batch.v[i] = out[PLACE_V]
            } else {
                batch.field[i] = member.own(p)
            }
        }
    }

    override val gathers: Boolean get() = false

    override val generation: Int get() = 0
}
