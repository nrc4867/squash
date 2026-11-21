package dev.chieppa.squash.character.surface

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** The square root of two, for the probit from the inverse error function. */
private const val SQRT_TWO = 1.4142135f

/** How far off 0 and 1 a share is held before its probit is read. */
private const val HELD_OFF = 0.9999f

/** The probit of [p]: where under a unit normal curve a share [p] of it lies below, [p] held off 0 and 1. */
internal fun probit(p: Float): Float {
    val x = (2f * p - 1f).coerceIn(-HELD_OFF, HELD_OFF)
    var w = -ln((1f - x) * (1f + x))
    var q: Float
    if (w < 5f) {
        w -= 2.5f
        q = 2.81022636e-08f
        q = 3.43273939e-07f + q * w
        q = -3.5233877e-06f + q * w
        q = -4.39150654e-06f + q * w
        q = 0.00021858087f + q * w
        q = -0.00125372503f + q * w
        q = -0.00417768164f + q * w
        q = 0.246640727f + q * w
        q = 1.50140941f + q * w
    } else {
        w = sqrt(w) - 3f
        q = -0.000200214257f
        q = 0.000100950558f + q * w
        q = 0.00134934322f + q * w
        q = -0.00367342844f + q * w
        q = 0.00573950773f + q * w
        q = -0.0076224613f + q * w
        q = 0.00943887047f + q * w
        q = 1.00167406f + q * w
        q = 2.83297682f + q * w
    }
    return SQRT_TWO * q * x
}

/** The share of a unit normal curve lying below [x]. */
internal fun normalShare(x: Float): Float {
    val z = abs(x) / SQRT_TWO
    val t = 1f / (1f + ERF_P * z)
    val poly = t * (ERF_A1 + t * (ERF_A2 + t * (ERF_A3 + t * (ERF_A4 + t * ERF_A5))))
    val erf = 1f - poly * exp(-z * z)
    return if (x >= 0f) 0.5f + 0.5f * erf else 0.5f - 0.5f * erf
}

private const val ERF_P = 0.3275911f
private const val ERF_A1 = 0.254829592f
private const val ERF_A2 = -0.284496736f
private const val ERF_A3 = 1.421413741f
private const val ERF_A4 = -1.453152027f
private const val ERF_A5 = 1.061405429f
