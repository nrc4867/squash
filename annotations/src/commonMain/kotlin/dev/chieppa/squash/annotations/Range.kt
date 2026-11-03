package dev.chieppa.squash.annotations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/** The numeric bounds of a template field; a schema `minimum` and `maximum`. NaN leaves that side open. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class Range(val min: Double = Double.NaN, val max: Double = Double.NaN)
