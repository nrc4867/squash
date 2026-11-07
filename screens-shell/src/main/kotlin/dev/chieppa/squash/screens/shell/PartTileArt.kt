package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.screens.sheet.PART_BASE
import dev.chieppa.squash.screens.sheet.PART_INK
import kotlin.math.min

private val ART_SIZE = Size(40f, 30f)
private const val ART_STROKE = 1.5f

/** The board's small filled shape for a template of each role the starter templates take, in a 40 by 30 box. */
private val TILE_ART = mapOf(
    Role.BODY to "M6 15 C6 7 34 7 34 15 C34 23 6 23 6 15 Z",
    Role.LEG to "M15 3 H25 L24 24 Q24 27 20 27 Q16 27 16 24 Z",
    Role.FOOT to "M32 24 C33 17 29 12 26 10 L14 10 C10 13 7 18 7 24 Z",
    Role.HEAD to "M20 4 A11 11 0 1 1 19.9 4 Z",
    Role.EAR to "M10 26 L20 3 L30 26 Z",
    Role.TAIL to "M6 25 C18 25 28 18 28 5 C36 12 36 26 22 28 C14 29 8 28 6 25 Z",
)

/** Whether a template of [role] has art of its own; one without, or with no role, shows the general parts icon. */
fun hasTileArt(role: Role?): Boolean = role in TILE_ART

/** The shape a template of [role] shows on its tile, in the character's colors. */
@Composable
fun PartTileArt(role: Role, modifier: Modifier = Modifier) {
    val path = remember(role) { Path().also { PathParser().parsePathString(TILE_ART.getValue(role)).toPath(it) } }
    Canvas(modifier) {
        val fit = min(size.width / ART_SIZE.width, size.height / ART_SIZE.height)
        translate((size.width - ART_SIZE.width * fit) / 2f, (size.height - ART_SIZE.height * fit) / 2f) {
            scale(fit, fit, pivot = Offset.Zero) {
                drawPath(path, PART_BASE)
                drawPath(path, PART_INK, style = Stroke(ART_STROKE, join = StrokeJoin.Round))
            }
        }
    }
}
