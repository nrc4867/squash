package dev.chieppa.squash.document

import kotlin.random.Random
import kotlin.time.Clock

private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private const val TIME_CHARS = 10
private const val RANDOM_CHARS = 16
private const val BITS_PER_CHAR = 5

/** Makes ULIDs: 48 bits of milliseconds then 80 random bits, in Crockford base 32, so they sort by creation time. */
object Ulid {
    fun next(millis: Long = Clock.System.now().toEpochMilliseconds(), random: Random = Random.Default): String {
        val time = CharArray(TIME_CHARS) { i ->
            ALPHABET[((millis ushr (BITS_PER_CHAR * (TIME_CHARS - 1 - i))) and (ALPHABET.length - 1L)).toInt()]
        }
        val rest = CharArray(RANDOM_CHARS) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return time.concatToString() + rest.concatToString()
    }
}
