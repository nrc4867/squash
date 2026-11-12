package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

/*
 * The twin's body outlines, in metres. Each is read from the reference's own numbers: the torso's ring table with the bust and seat
 * swellings, the arm's and leg's radius keys with the calf behind and the knee and wrist pressed, the palm and the foot's blocks.
 */

/** The torso's drawn length, from the bottom of the pelvis to the base of the neck. */
internal const val TORSO_LENGTH = 0.66f

/** Height above the ground of the torso's start: the bottom of the pelvis, as the reference stands at rest. */
internal const val PELVIS_HEIGHT = 0.735f

/** The torso in Side, front up: the pelvis's round bottom, the seat behind the hips, the waist, the bust under the chest, the shoulders and the base of the neck. */
internal val TORSO_SIDE = loop(
    p(0.000f, -0.004f), p(0.001f, 0.0166f), p(0.009f, 0.0459f), p(0.021f, 0.0655f), p(0.037f, 0.0773f), p(0.050f, 0.0800f), p(0.065f, 0.0837f),
    p(0.085f, 0.0880f), p(0.115f, 0.0922f), p(0.145f, 0.0940f), p(0.175f, 0.0930f), p(0.215f, 0.0900f), p(0.255f, 0.0847f), p(0.295f, 0.0810f),
    p(0.330f, 0.0837f), p(0.365f, 0.0888f), p(0.405f, 0.1069f), p(0.430f, 0.1331f), p(0.455f, 0.1488f), p(0.480f, 0.1438f), p(0.505f, 0.1289f),
    p(0.525f, 0.1137f), p(0.550f, 0.0929f), p(0.570f, 0.0779f), p(0.590f, 0.0654f), p(0.605f, 0.0577f), p(0.620f, 0.0507f), p(0.635f, 0.0452f),
    p(0.650f, 0.0416f), p(0.660f, 0.000f), p(0.650f, -0.0638f), p(0.635f, -0.0680f), p(0.620f, -0.0728f), p(0.605f, -0.0770f), p(0.590f, -0.0802f),
    p(0.570f, -0.0830f), p(0.550f, -0.0834f), p(0.525f, -0.0820f), p(0.505f, -0.0808f), p(0.480f, -0.0800f), p(0.455f, -0.0793f), p(0.430f, -0.0783f),
    p(0.405f, -0.0765f), p(0.365f, -0.0740f), p(0.330f, -0.0732f), p(0.295f, -0.0732f), p(0.255f, -0.0795f), p(0.215f, -0.0946f), p(0.175f, -0.1215f),
    p(0.145f, -0.1380f), p(0.115f, -0.1380f), p(0.085f, -0.1220f), p(0.065f, -0.1016f), p(0.050f, -0.0899f), p(0.037f, -0.0871f), p(0.021f, -0.0748f),
    p(0.009f, -0.0541f), p(0.001f, -0.0234f),
)

/** The torso from in front, its half width: the hips widest, in to the waist, out over the ribs to the shoulder line, in to the neck. */
internal val TORSO_TOP = loop(
    p(0.000f, 0.000f), p(0.001f, 0.0312f), p(0.009f, 0.0860f), p(0.021f, 0.1229f), p(0.037f, 0.1449f), p(0.050f, 0.1500f), p(0.065f, 0.1591f),
    p(0.085f, 0.1700f), p(0.115f, 0.1823f), p(0.145f, 0.1880f), p(0.175f, 0.1846f), p(0.215f, 0.1720f), p(0.255f, 0.1455f), p(0.295f, 0.1270f),
    p(0.330f, 0.1284f), p(0.365f, 0.1321f), p(0.405f, 0.1399f), p(0.430f, 0.1455f), p(0.455f, 0.1497f), p(0.480f, 0.1522f), p(0.505f, 0.1544f),
    p(0.525f, 0.1567f), p(0.550f, 0.1623f), p(0.570f, 0.1651f), p(0.590f, 0.1553f), p(0.605f, 0.1381f), p(0.620f, 0.1097f), p(0.635f, 0.0801f),
    p(0.650f, 0.0595f), p(0.660f, 0.000f), p(0.650f, -0.0595f), p(0.635f, -0.0801f), p(0.620f, -0.1097f), p(0.605f, -0.1381f), p(0.590f, -0.1553f),
    p(0.570f, -0.1651f), p(0.550f, -0.1623f), p(0.525f, -0.1567f), p(0.505f, -0.1544f), p(0.480f, -0.1522f), p(0.455f, -0.1497f), p(0.430f, -0.1455f),
    p(0.405f, -0.1399f), p(0.365f, -0.1321f), p(0.330f, -0.1284f), p(0.295f, -0.1270f), p(0.255f, -0.1455f), p(0.215f, -0.1720f), p(0.175f, -0.1846f),
    p(0.145f, -0.1880f), p(0.115f, -0.1823f), p(0.085f, -0.1700f), p(0.065f, -0.1591f), p(0.050f, -0.1500f), p(0.037f, -0.1449f), p(0.021f, -0.1229f),
    p(0.009f, -0.0860f), p(0.001f, -0.0312f),
)

/** The neck from the torso's top to the head's origin, a little deeper behind than in front, front up in Side. */
internal const val NECK_LENGTH = 0.065f
internal val NECK_SIDE = drawn(p(0f, 0.052f), p(0.015f, 0.050f), p(0.045f, 0.048f), p(0.065f, 0f), p(0.045f, -0.048f), p(0.015f, -0.051f), p(0f, -0.054f))
internal val NECK_TOP = drawn(p(0f, 0.056f), p(0.015f, 0.052f), p(0.045f, 0.048f), p(0.065f, 0f), p(0.045f, -0.048f), p(0.015f, -0.052f), p(0f, -0.056f))

/** The arm from the shoulder joint to the wrist: round, slimming to the elbow, the forearm's swell, the wrist deeper than wide. */
internal const val ARM_LENGTH = 0.512f
internal val ARM_SIDE = drawn(
    p(0f, 0.046f), p(0.075f, 0.043f), p(0.134f, 0.039f), p(0.209f, 0.035f), p(0.258f, 0.031f), p(0.308f, 0.034f), p(0.360f, 0.0305f),
    p(0.422f, 0.0273f), p(0.470f, 0.0255f), p(0.497f, 0.0252f), p(0.512f, 0f), p(0.497f, -0.0252f), p(0.470f, -0.0255f), p(0.422f, -0.0273f),
    p(0.360f, -0.0305f), p(0.308f, -0.034f), p(0.258f, -0.031f), p(0.209f, -0.035f), p(0.134f, -0.039f), p(0.075f, -0.043f), p(0.000f, -0.046f),
)
internal val ARM_TOP = drawn(
    p(0f, 0.046f), p(0.075f, 0.043f), p(0.134f, 0.039f), p(0.209f, 0.035f), p(0.258f, 0.031f), p(0.308f, 0.034f), p(0.360f, 0.0300f),
    p(0.422f, 0.0263f), p(0.470f, 0.0238f), p(0.497f, 0.0228f), p(0.512f, 0f), p(0.497f, -0.0228f), p(0.470f, -0.0238f), p(0.422f, -0.0263f),
    p(0.360f, -0.0300f), p(0.308f, -0.034f), p(0.258f, -0.031f), p(0.209f, -0.035f), p(0.134f, -0.039f), p(0.075f, -0.043f), p(0.000f, -0.046f),
)

/**
 * The leg from where it leaves the pelvis's underside to the ankle, front up in Side: the thigh below the hip, the knee, the calf swelling
 * behind, a slim ankle. Above that the pelvis holds the top of the thigh.
 */
internal const val LEG_LENGTH = 0.684f
internal const val KNEE_ALONG = 0.274f
internal val LEG_SIDE = drawn(
    p(0f, 0.085f), p(0.040f, 0.081f), p(0.118f, 0.072f), p(0.251f, 0.053f), p(0.290f, 0.051f), p(0.368f, 0.056f), p(0.477f, 0.047f),
    p(0.602f, 0.034f), p(0.665f, 0.031f), p(0.684f, 0f), p(0.665f, -0.031f), p(0.602f, -0.034f), p(0.477f, -0.0485f), p(0.423f, -0.0598f),
    p(0.368f, -0.067f), p(0.313f, -0.0596f), p(0.290f, -0.0526f), p(0.251f, -0.0535f), p(0.118f, -0.072f), p(0.040f, -0.081f), p(0f, -0.085f),
)
internal val LEG_TOP = drawn(
    p(0f, 0.085f), p(0.040f, 0.0835f), p(0.118f, 0.0735f), p(0.251f, 0.050f), p(0.274f, 0.048f), p(0.368f, 0.0545f),
    p(0.477f, 0.047f), p(0.602f, 0.034f), p(0.665f, 0.031f), p(0.684f, 0f), p(0.665f, -0.031f), p(0.602f, -0.034f), p(0.477f, -0.047f),
    p(0.368f, -0.0545f), p(0.274f, -0.048f), p(0.251f, -0.050f), p(0.118f, -0.0735f), p(0.040f, -0.0835f), p(0f, -0.085f),
)

/** The unit the shoes are drawn in, in metres. */
internal const val FOOT_SIZE = 0.443f
