package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.shownSet
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.SetExpression
import dev.chieppa.squash.document.ShowExpression
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_delete_expression
import dev.chieppa.squash.uidesign.generated.face_expression_name
import dev.chieppa.squash.uidesign.generated.face_neutral
import dev.chieppa.squash.uidesign.generated.face_new_expression
import dev.chieppa.squash.uidesign.generated.face_version_front
import dev.chieppa.squash.uidesign.generated.face_version_side
import dev.chieppa.squash.uidesign.generated.face_version_three_quarter
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Card
import dev.chieppa.stretch.ui.IconButton
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val TILE = 88.dp
private val GAP = 6.dp
private val TILE_PAD = 6.dp
private const val MARK_STROKE = 1.5f

private const val NEW_ID_PREFIX = "expression-"

/** What a thumbnail leaves out, since a small drawing of plain lines reads better without rings inside rings. */
private val THUMBNAIL_SKIPS = setOf(MarkRole.WHITE, MarkRole.PUPIL, MarkRole.HIGHLIGHT, MarkRole.TONGUE)

/** Test tags for the Expressions and Angle versions drawers. */
object ExpressionTags {
    const val NEUTRAL = "expressions.neutral"
    const val NEW = "expressions.new"
    const val DELETE = "expressions.delete"

    fun tile(id: ExpressionId) = "expressions.tile.${id.value}"
    fun version(bin: CameraBin) = "versions.tile.${bin.value}"
}

private val VERSION_NAMES = mapOf(
    FACE_SIDE to Res.string.face_version_side,
    FACE_THREE_QUARTER to Res.string.face_version_three_quarter,
    FACE_FRONT to Res.string.face_version_front,
)

/** How a view of a version looks at the head, in the head's forward [forward]: side on, three-quarter or ahead, as the bins read. */
private fun toViewerIn(bin: CameraBin, forward: Vec3): Vec3 {
    val side = (Vec3.Y cross forward).normalized()
    return when (bin) {
        FACE_FRONT -> forward
        FACE_THREE_QUARTER -> (forward + side).normalized()
        else -> side
    }
}

/** The face marks of [work] as [bin] and [expressed] show them, seen from that bin's side, as a small drawing of plain lines. */
@Composable
private fun FaceThumbnail(work: ResolvedSurfaceWork?, expressed: ExpressionId?, bin: CameraBin, forward: Vec3, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val held = work ?: return@Canvas
        val set = held.expressions.firstOrNull { it.id == expressed }
        val marks = held.marks.associateBy { it.id }
        val lines = ArrayList<ResolvedMark>()
        for (group in held.groups) {
            for (role in group.roles) {
                val own = set?.marks?.get(group.id)?.get(role.role) ?: role.marks.mapNotNull(marks::get)
                if (role.role !in THUMBNAIL_SKIPS) lines += own.filter { it.kind == MarkKind.LINE }
            }
        }
        val toViewer = toViewerIn(bin, forward)
        val right = (Vec3.Y cross toViewer).normalized()
        val up = toViewer cross right
        val places = lines.map { mark -> mark.pointsIn(bin).filter { (it.normal dot toViewer) > 0f }.map { Offset(it.position dot right, -(it.position dot up)) } }
        val all = places.flatten()
        if (all.isEmpty()) return@Canvas
        val low = Offset(all.minOf { it.x }, all.minOf { it.y })
        val high = Offset(all.maxOf { it.x }, all.maxOf { it.y })
        val span = maxOf(high.x - low.x, high.y - low.y, 1e-3f)
        val scale = minOf(size.width, size.height) / span
        val middle = Offset((low.x + high.x) / 2f, (low.y + high.y) / 2f)
        val center = Offset(size.width / 2f, size.height / 2f)
        for (line in places) {
            line.map { center + (it - middle) * scale }.zipWithNext().forEach { (a, b) -> drawLine(ink, a, b, MARK_STROKE) }
        }
    }
}

@Composable
private fun Tile(name: String, selected: Boolean, tag: String, onClick: () -> Unit, art: @Composable (Modifier) -> Unit) {
    val c = Stretch.colors
    Card(Modifier.size(TILE).testTag(tag), onClick, selected) {
        Column(Modifier.fillMaxSize().padding(TILE_PAD), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f)) { art(Modifier.fillMaxSize()) }
            BasicText(name, style = Stretch.type.bodySm.merge(TextStyle(color = c.ink)), maxLines = 1)
        }
    }
}

/** The part holding the character's face, its work and which way it faces. */
private class FaceHolder(val work: ResolvedSurfaceWork?, val forward: Vec3)

@Composable
private fun rememberFaceHolder(session: CharacterSession): FaceHolder {
    val work by session.bodySurfaceWork.output.collectAsState()
    val assembled by session.assembly.output.collectAsState()
    val entry = work?.parts?.entries?.firstOrNull { it.value.groups.isNotEmpty() }
    val part = entry?.let { assembled?.part(it.key) }
    val forward = part?.let { dev.chieppa.squash.character.build.PartBuilder.axes(it.template).along } ?: Vec3.X
    return FaceHolder(entry?.value, forward)
}

/** The Expressions drawer: Neutral and each of the character's expressions, as tiles that show the expression on the character. */
@Composable
fun ExpressionsDrawer(state: AppState) {
    val open by state.session.collectAsState()
    val session = open ?: return
    val document by session.document.collectAsState()
    val character = document.character
    val holder = rememberFaceHolder(session)
    val shown = character.shownSet?.id
    val ink = Stretch.colors.ink
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(GAP), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Tile(stringResource(Res.string.face_neutral), shown == null, ExpressionTags.NEUTRAL, { session.send(ShowExpression(null)) }) {
            FaceThumbnail(holder.work, null, FACE_SIDE, holder.forward, ink, it)
        }
        character.expressions.forEach { set ->
            Tile(set.name, shown == set.id, ExpressionTags.tile(set.id), { session.send(ShowExpression(set.id)) }) {
                FaceThumbnail(holder.work, set.id, FACE_SIDE, holder.forward, ink, it)
            }
        }
    }
}

/** The Expressions drawer's header: add an expression, and delete the shown one. */
@Composable
fun RowScope.ExpressionActions(state: AppState) {
    val open by state.session.collectAsState()
    val session = open ?: return
    val document by session.document.collectAsState()
    val character = document.character
    val scope = rememberCoroutineScope()
    val add = stringResource(Res.string.face_new_expression)
    val delete = stringResource(Res.string.face_delete_expression)
    IconButton(SquashIcons.AddSlot, add, {
        scope.launch {
            val id = freeExpressionId(character)
            session.send(SetExpression(id, ExpressionSet(id, getString(Res.string.face_expression_name, character.expressions.size + 1))))
            session.send(ShowExpression(id))
        }
    }, Modifier.testTag(ExpressionTags.NEW))
    character.shownSet?.let { set ->
        IconButton(SquashIcons.RemoveSlot, delete, {
            session.send(ShowExpression(null))
            session.send(SetExpression(set.id, null))
        }, Modifier.testTag(ExpressionTags.DELETE))
    }
}

private fun freeExpressionId(character: Character): ExpressionId {
    val held = character.expressions.map { it.id.value }.toSet()
    return generateSequence(1) { it + 1 }.map { "$NEW_ID_PREFIX$it" }.first { it !in held }.let(::ExpressionId)
}

/** The Angle versions drawer: the shown expression's three versions, whose tiles choose which the face tools draw in; picking the chosen one lets the view choose again. */
@Composable
fun AngleVersionsDrawer(state: AppState) {
    val open by state.session.collectAsState()
    val session = open ?: return
    val document by session.document.collectAsState()
    val holder = rememberFaceHolder(session)
    val shown = document.character.shownSet?.id
    val ink = Stretch.colors.ink
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(GAP), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        FACE_BINS.bins.forEach { bin ->
            Tile(stringResource(VERSION_NAMES.getValue(bin)), state.face.version == bin, ExpressionTags.version(bin), { state.face.version = bin.takeUnless { state.face.version == it } }) {
                FaceThumbnail(holder.work, shown, bin, holder.forward, ink, it)
            }
        }
    }
}
