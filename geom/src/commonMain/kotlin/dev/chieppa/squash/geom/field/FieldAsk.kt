package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

/**
 * One piece of work's reads of [members]' fields, a round at a time: the work asks for every point it needs next with [own] or [place],
 * waits for [answered], then reads each answer by the index its ask gave. Where [reads] does not gather, each ask is read at once and
 * [answered] does not wait; otherwise the work stops at [answered] until a [ReadRounds] has answered every piece's round as one batch.
 */
class FieldAsk internal constructor(private val reads: FieldReads, members: List<JoinedMember>) {
    internal val batch = FieldBatch(members)
    private var round = false
    private val at = FloatArray(PLACE_ARRAY)
    internal var waiting: Continuation<Unit>? = null

    /** Whether asks wait for a round to be answered together; see [FieldReads.gathers]. */
    val gathers: Boolean get() = reads.gathers

    /** Asks for member [m]'s own field at body point [p]; its answer is [field] of the index returned. */
    fun own(m: Int, p: Vec3): Int = ask(m, p, false)

    /** Asks for member [m]'s own field at body point [p] and where it lies on its run; see [JoinedMember.place]. */
    fun place(m: Int, p: Vec3): Int = ask(m, p, true)

    private fun ask(m: Int, p: Vec3, placed: Boolean): Int {
        if (round) {
            batch.clear()
            round = false
        }
        val i = batch.add(m, p, placed)
        if (!reads.gathers) {
            val member = batch.members[m]
            if (placed) {
                member.place(p, at)
                batch.field[i] = at[PLACE_FIELD]
                batch.u[i] = at[PLACE_U]
                batch.v[i] = at[PLACE_V]
            } else {
                batch.field[i] = member.own(p)
            }
        }
        return i
    }

    /** Returns once every point asked since the last round is answered. */
    suspend fun answered() {
        val asked = !round && batch.size > 0
        round = true
        if (!reads.gathers || !asked) return
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            waiting = continuation
            COROUTINE_SUSPENDED
        }
    }

    /**
     * Reads every point asked since the last round at once, on the asking thread through the members' own solids, wherever [reads]
     * would gather them: for rounds so small that a batch of their own costs more than reading them here. A piece of work that reads a
     * given step this way always does, so the values it reads stay the same from one run to the next.
     */
    fun answeredHere() {
        if (reads.gathers && !round) CpuFieldReads.read(batch)
        round = true
    }

    fun field(i: Int): Float = batch.field[i]

    fun u(i: Int): Float = batch.u[i]

    fun v(i: Int): Float = batch.v[i]
}

/**
 * [count] pieces of work over one field's [members], each run by [job] with its own [FieldAsk], made into batches of work: [tasks] runs
 * each piece on to its next round of reads, or to its end, [chunk] pieces a task; [answer] then reads every piece's round as one batch
 * through [reads]. Where [reads] does not gather, the first [tasks] runs every piece to its end.
 */
class ReadRounds(
    private val reads: FieldReads,
    private val members: List<JoinedMember>,
    private val count: Int,
    private val chunk: Int,
    private val job: suspend FieldAsk.(Int) -> Unit,
) {
    private val asks = arrayOfNulls<FieldAsk>(count)
    private var started = false
    private var failure: Throwable? = null

    /** Pieces of work, each running [chunk] of the pieces on: started on the first call, resumed after an [answer] on later ones. */
    fun tasks(): List<() -> Unit> {
        answered = false
        val first = !started
        started = true
        return (0 until count step chunk).map { from ->
            {
                val shared = if (reads.gathers) null else FieldAsk(reads, members)
                for (i in from until minOf(count, from + chunk)) {
                    if (first) start(i, shared ?: FieldAsk(reads, members)) else resume(i)
                }
            }
        }
    }

    private fun start(i: Int, ask: FieldAsk) {
        if (reads.gathers) asks[i] = ask
        val body: suspend () -> Unit = { ask.job(i) }
        body.startCoroutine(Done)
        failure?.let { throw it }
    }

    private fun resume(i: Int) {
        val ask = asks[i] ?: return
        val waiting = ask.waiting ?: return
        ask.waiting = null
        waiting.resume(Unit)
        failure?.let { throw it }
    }

    /** Whether a piece waits for its round to be answered; false once every piece has run to its end. */
    fun waiting(): Boolean = asks.any { it?.waiting != null }

    /** Whether the waiting pieces' round has been answered, so [tasks] goes on with them. */
    var answered: Boolean = false
        private set

    /** Reads every waiting piece's round as one batch; false when no piece waits, every one having run to its end. */
    fun answer(): Boolean = waiting().also { if (it) answerTogether(reads, listOf(this)) }

    /** Copies every waiting piece's queries into [into], their members found [offset] on in its members. */
    private fun gather(into: FieldBatch, offset: Int) {
        for (ask in asks) if (ask?.waiting != null) into.addAll(ask.batch, offset = offset)
    }

    /** Copies the answers from [from]'s query [start] on into every waiting piece's in turn; returns where the next one's start. */
    private fun scatter(from: FieldBatch, start: Int): Int {
        var at = start
        for (ask in asks) if (ask?.waiting != null) {
            from.answersInto(ask.batch, at)
            at += ask.batch.size
        }
        answered = true
        return at
    }

    companion object {
        /** Reads the waiting rounds of every one of [rounds] as one batch through [reads]; their members may differ. */
        fun answerTogether(reads: FieldReads, rounds: List<ReadRounds>) {
            val batch = FieldBatch(rounds.flatMap { it.members })
            var offset = 0
            for (r in rounds) {
                r.gather(batch, offset)
                offset += r.members.size
            }
            reads.read(batch)
            var at = 0
            for (r in rounds) at = r.scatter(batch, at)
        }
    }

    private val Done = object : Continuation<Unit> {
        override val context: CoroutineContext get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.exceptionOrNull()?.let { failure = it }
        }
    }
}
