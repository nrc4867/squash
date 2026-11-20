package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.document.FaceStyle
import dev.chieppa.squash.document.KeyFeature
import dev.chieppa.squash.document.KeyMove
import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.ViewKey

/** How far the muzzle turns and slides toward the camera at three-quarter, in degrees and shares of the head's length. */
private const val MUZZLE_THREE_QUARTER_YAW = 9f
private const val MUZZLE_THREE_QUARTER_LEFT = 0.03f

/** How far it turns toward the camera side on, where the slide would only move it in depth. */
private const val MUZZLE_SIDE_YAW = 4f

/** How far it turns and slides toward the camera from three-quarter behind, so its tip shows past the cheek. */
private const val MUZZLE_BACK_YAW = 16f
private const val MUZZLE_BACK_LEFT = 0.05f

/** How far each ear turns its opening toward the camera at three-quarter and side on, and its back from three-quarter behind. */
private const val EARS_THREE_QUARTER_TWIST = 14f
private const val EARS_SIDE_TWIST = 32f
private const val EARS_BACK_TWIST = -18f

/** How much wider and how much less tall the head is from behind, and from three-quarter behind, than it is built. */
private const val BACK_WIDTH = 1.05f
private const val BACK_HEIGHT = 0.97f
private const val BACK_THREE_QUARTER_WIDTH = 1.025f

/** How far a turning face's near cheek fills out at three-quarter and side on, and its chin slides toward the camera at three-quarter. */
private const val TURNING_NEAR_CHEEK = 0.025f
private const val TURNING_SIDE_CHEEK = 0.015f
private const val TURNING_CHIN_SLIDE = 0.025f

/** The chin's area on a head template. */
private const val CHIN = "chin"

/**
 * Every head's back, in either face style: a little wider and lower from behind, as a sheet draws the back of a head on its own rather
 * than as the front turned round.
 */
val BACK_SHAPE_KEYS: List<ViewKey> = listOf(
    ViewKey(KeyView.THREE_QUARTER_BACK, moves = listOf(KeyMove(KeyFeature.HEAD, width = BACK_THREE_QUARTER_WIDTH))),
    ViewKey(KeyView.BACK, moves = listOf(KeyMove(KeyFeature.HEAD, width = BACK_WIDTH, height = BACK_HEIGHT))),
)

/**
 * A turning face's head shape, as seen with the camera on the head's left: its near cheek fills out and its chin slides toward the
 * camera at three-quarter, and the cheek a little side on, so the outline changes as the head turns.
 */
val TURNING_SHAPE_KEYS: List<ViewKey> = listOf(
    ViewKey(
        KeyView.THREE_QUARTER,
        moves = listOf(KeyMove(KeyFeature.HEAD, nearPush = TURNING_NEAR_CHEEK), KeyMove(KeyFeature.HEAD, area = CHIN, left = TURNING_CHIN_SLIDE)),
    ),
    ViewKey(KeyView.SIDE, moves = listOf(KeyMove(KeyFeature.HEAD, nearPush = TURNING_SIDE_CHEEK))),
)

/** Every head's default keys in face style [style]: [DEFAULT_VIEW_KEYS], its style's shape keys and the back's. */
fun defaultKeys(style: FaceStyle): List<ViewKey> =
    DEFAULT_VIEW_KEYS + BACK_SHAPE_KEYS + if (style == FaceStyle.TURNING) TURNING_SHAPE_KEYS else emptyList()

/**
 * Every head's keys with nothing authored, as seen with the camera on the head's left: its muzzle leans toward the camera, more from
 * behind, where it would hide behind the cheek, and its ears turn their broad side toward the camera, toward profile, so neither
 * collapses into a sliver as the head turns. The front and back keep the head as it rests.
 */
val DEFAULT_VIEW_KEYS: List<ViewKey> = listOf(
    ViewKey(
        KeyView.THREE_QUARTER,
        moves = listOf(
            KeyMove(KeyFeature.MUZZLE, yaw = MUZZLE_THREE_QUARTER_YAW, left = MUZZLE_THREE_QUARTER_LEFT),
            KeyMove(KeyFeature.EARS, twist = EARS_THREE_QUARTER_TWIST),
        ),
    ),
    ViewKey(
        KeyView.SIDE,
        moves = listOf(
            KeyMove(KeyFeature.MUZZLE, yaw = MUZZLE_SIDE_YAW),
            KeyMove(KeyFeature.EARS, twist = EARS_SIDE_TWIST),
        ),
    ),
    ViewKey(
        KeyView.THREE_QUARTER_BACK,
        moves = listOf(
            KeyMove(KeyFeature.MUZZLE, yaw = MUZZLE_BACK_YAW, left = MUZZLE_BACK_LEFT),
            KeyMove(KeyFeature.EARS, twist = EARS_BACK_TWIST),
        ),
    ),
)
