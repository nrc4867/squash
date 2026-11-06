package dev.chieppa.squash.screens.references

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** A [width] by [height] PNG, cream with a dark band across its lower third and a red left half above it, to trace over. */
fun sketchPng(width: Int = 320, height: Int = 200): ByteArray {
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.clear(Color.makeRGB(0xF4, 0xE6, 0xC8))
    surface.canvas.drawRect(Rect(0f, 0f, width / 2f, height * 2f / 3f), Paint().apply { color = Color.makeRGB(0xC0, 0x30, 0x30) })
    surface.canvas.drawRect(Rect(0f, height * 2f / 3f, width.toFloat(), height.toFloat()), Paint().apply { color = Color.makeRGB(0x1C, 0x15, 0x17) })
    return requireNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
}
