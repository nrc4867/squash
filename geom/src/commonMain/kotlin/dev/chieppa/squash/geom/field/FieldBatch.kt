package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/**
 * Queries of [members]' own fields: for each, the [member] it reads by its place in [members], its body point ([x], [y], [z]) and whether
 * it is [place]d, which also reads its [u] and [v] on the member's run; [field] and those two are the answers, filled by [FieldReads.read].
 */
class FieldBatch(val members: List<JoinedMember>, capacity: Int = INITIAL) {
    var size: Int = 0
        private set
    var member: IntArray = IntArray(capacity)
        private set
    var x: FloatArray = FloatArray(capacity)
        private set
    var y: FloatArray = FloatArray(capacity)
        private set
    var z: FloatArray = FloatArray(capacity)
        private set
    var place: BooleanArray = BooleanArray(capacity)
        private set
    var field: FloatArray = FloatArray(capacity)
        private set
    var u: FloatArray = FloatArray(capacity)
        private set
    var v: FloatArray = FloatArray(capacity)
        private set

    /** Query [i]'s point. */
    fun point(i: Int): Vec3 = Vec3(x[i], y[i], z[i])

    /** Adds a query of member [m] at [p], placed when [placed]; returns its index. */
    fun add(m: Int, p: Vec3, placed: Boolean): Int {
        if (size == member.size) grow(size * 2)
        member[size] = m
        x[size] = p.x
        y[size] = p.y
        z[size] = p.z
        place[size] = placed
        return size++
    }

    /** Copies [count] queries of [from] from its query [start] on to the end of this batch, their members [offset] further on. */
    fun addAll(from: FieldBatch, start: Int = 0, count: Int = from.size - start, offset: Int = 0) {
        if (size + count > member.size) grow(maxOf(member.size * 2, size + count))
        if (offset == 0) {
            from.member.copyInto(member, size, start, start + count)
        } else {
            for (i in 0 until count) member[size + i] = from.member[start + i] + offset
        }
        from.x.copyInto(x, size, start, start + count)
        from.y.copyInto(y, size, start, start + count)
        from.z.copyInto(z, size, start, start + count)
        from.place.copyInto(place, size, start, start + count)
        size += count
    }

    /** Copies the answers of this batch's queries from [start] on into [into]'s, from its first. */
    fun answersInto(into: FieldBatch, start: Int, count: Int = into.size) {
        field.copyInto(into.field, 0, start, start + count)
        u.copyInto(into.u, 0, start, start + count)
        v.copyInto(into.v, 0, start, start + count)
    }

    /** Forgets every query, keeping the room they took. */
    fun clear() {
        size = 0
    }

    private fun grow(to: Int) {
        member = member.copyOf(to)
        x = x.copyOf(to)
        y = y.copyOf(to)
        z = z.copyOf(to)
        place = place.copyOf(to)
        field = field.copyOf(to)
        u = u.copyOf(to)
        v = v.copyOf(to)
    }

    private companion object {
        const val INITIAL = 16
    }
}
