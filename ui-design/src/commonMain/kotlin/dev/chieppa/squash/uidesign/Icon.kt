package dev.chieppa.squash.uidesign

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

internal class IconScope(private val builder: ImageVector.Builder) {
    fun stroke(d: String) {
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fill(d: String) {
        builder.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
    }
}

internal fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(name, 16.dp, 16.dp, 16f, 16f)
    IconScope(builder).block()
    return builder.build()
}
