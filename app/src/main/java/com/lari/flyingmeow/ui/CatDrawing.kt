package com.lari.flyingmeow.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

/** The playable cats. Everything is drawn on Canvas, no images at all. */
enum class CatSkin(
    val label: String,
    val fur: Color,
    val furDark: Color,
    val muzzle: Color,
    val hasStripes: Boolean = false,
    val hasMask: Boolean = false,
) {
    Ginger("Ginger", Color(0xFFF59E3B), Color(0xFFC96A12), Color(0xFFFFE2BF), hasStripes = true),
    Shadow("Shadow", Color(0xFF2B2B33), Color(0xFF14141A), Color(0xFF3D3D47)),
    Siamese("Siamese", Color(0xFFF3E6D2), Color(0xFF6B4A35), Color(0xFFF8EFE2), hasMask = true),
    Smokey("Smokey", Color(0xFF9AA3AE), Color(0xFF6C7580), Color(0xFFE4E8EC), hasStripes = true),
}

/** The face changes with the game state, each one is a nod to a different cat meme. */
enum class CatMood {
    Smug,    // ready to play: half-closed "smug cat" eyes
    Shocked, // right after a flap: wide, startled eyes
    Crying,  // game over: the classic crying cat
}

private val Pink = Color(0xFFFF8FA3)
private val Ink = Color(0xFF1C1B22)
private val Tear = Color(0xFF7CC8FF)

fun DrawScope.drawCat(
    center: Offset,
    r: Float,
    rotation: Float,
    skin: CatSkin,
    mood: CatMood,
    time: Float,
    timeSinceFlap: Float,
) = rotate(rotation, pivot = center) {
    val outline = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
    val dark = if (skin == CatSkin.Shadow) Color(0xFF55555F) else skin.furDark

    // Tail wagging behind the body
    val wag = sin(time * 9f) * r * 0.35f
    val tail = Path().apply {
        moveTo(center.x - r * 0.8f, center.y + r * 0.25f)
        quadraticTo(
            center.x - r * 1.7f, center.y + r * 0.1f + wag,
            center.x - r * 1.55f, center.y - r * 0.75f + wag,
        )
    }
    drawPath(tail, skin.furDark, style = Stroke(width = r * 0.32f, cap = StrokeCap.Round))
    drawPath(tail, skin.fur, style = Stroke(width = r * 0.2f, cap = StrokeCap.Round))

    // Ears
    for (side in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(center.x + side * r * 0.85f, center.y - r * 0.3f)
            lineTo(center.x + side * r * 0.75f, center.y - r * 1.3f)
            lineTo(center.x + side * r * 0.15f, center.y - r * 0.85f)
            close()
        }
        drawPath(ear, if (skin.hasMask) skin.furDark else skin.fur)
        drawPath(ear, dark, style = outline)
        val inner = Path().apply {
            moveTo(center.x + side * r * 0.72f, center.y - r * 0.5f)
            lineTo(center.x + side * r * 0.68f, center.y - r * 1.08f)
            lineTo(center.x + side * r * 0.32f, center.y - r * 0.82f)
            close()
        }
        drawPath(inner, Pink.copy(alpha = 0.8f))
    }

    // Round head (the "body" of the original bird)
    drawCircle(skin.fur, r, center)
    if (skin.hasMask) {
        drawOval(
            skin.furDark.copy(alpha = 0.85f),
            topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.2f),
            size = Size(r * 1.24f, r * 0.95f),
        )
    }
    if (skin.hasStripes) {
        for (i in -1..1) {
            val x = center.x + i * r * 0.28f
            drawLine(
                skin.furDark, Offset(x, center.y - r * 0.95f), Offset(x, center.y - r * 0.6f),
                strokeWidth = r * 0.12f, cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(dark, r, center, style = outline)

    // Muzzle
    drawOval(
        skin.muzzle,
        topLeft = Offset(center.x - r * 0.42f, center.y + r * 0.08f),
        size = Size(r * 0.84f, r * 0.55f),
    )

    // Rosy cheeks
    for (side in listOf(-1f, 1f)) {
        drawCircle(Pink.copy(alpha = 0.45f), r * 0.16f, Offset(center.x + side * r * 0.62f, center.y + r * 0.22f))
    }

    drawEyes(center, r, mood)

    // Nose and "w" mouth
    val nose = Path().apply {
        moveTo(center.x - r * 0.1f, center.y + r * 0.14f)
        lineTo(center.x + r * 0.1f, center.y + r * 0.14f)
        lineTo(center.x, center.y + r * 0.26f)
        close()
    }
    drawPath(nose, Pink)
    drawMouth(center, r, mood, time)

    // Whiskers
    val whiskerColor = if (skin == CatSkin.Shadow) Color(0xFFD9D9E0) else Ink.copy(alpha = 0.7f)
    for (side in listOf(-1f, 1f)) {
        for (k in listOf(-0.12f, 0.04f, 0.2f)) {
            drawLine(
                whiskerColor,
                Offset(center.x + side * r * 0.45f, center.y + r * (0.3f + k * 0.3f)),
                Offset(center.x + side * r * 1.25f, center.y + r * (0.2f + k)),
                strokeWidth = r * 0.035f, cap = StrokeCap.Round,
            )
        }
    }

    // Paws: they "flap" up on every jump, in place of wings
    val flapLift = (1f - (timeSinceFlap / 0.18f)).coerceIn(0f, 1f)
    val pawY = center.y + r * (0.92f - flapLift * 0.55f)
    for (side in listOf(-1f, 1f)) {
        val pawCenter = Offset(center.x + side * r * 0.42f, pawY)
        drawOval(
            skin.fur,
            topLeft = Offset(pawCenter.x - r * 0.22f, pawCenter.y - r * 0.15f),
            size = Size(r * 0.44f, r * 0.3f),
        )
        drawOval(
            dark,
            topLeft = Offset(pawCenter.x - r * 0.22f, pawCenter.y - r * 0.15f),
            size = Size(r * 0.44f, r * 0.3f),
            style = Stroke(width = r * 0.05f),
        )
        drawCircle(Pink, r * 0.05f, Offset(pawCenter.x, pawCenter.y + r * 0.02f))
    }
}

private fun DrawScope.drawEyes(center: Offset, r: Float, mood: CatMood) {
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(center.x + side * r * 0.36f, center.y - r * 0.15f)
        when (mood) {
            CatMood.Smug -> {
                // Lowered eyelid, judging you
                drawOval(Color.White, Offset(eye.x - r * 0.17f, eye.y - r * 0.08f), Size(r * 0.34f, r * 0.2f))
                drawOval(Ink, Offset(eye.x - r * 0.08f, eye.y - r * 0.06f), Size(r * 0.16f, r * 0.16f))
                drawLine(
                    Ink, Offset(eye.x - r * 0.2f, eye.y - r * 0.08f), Offset(eye.x + r * 0.2f, eye.y - r * 0.08f),
                    strokeWidth = r * 0.07f, cap = StrokeCap.Round,
                )
            }
            CatMood.Shocked -> {
                drawCircle(Color.White, r * 0.22f, eye)
                drawCircle(Ink, r * 0.22f, eye, style = Stroke(r * 0.04f))
                drawCircle(Ink, r * 0.07f, eye)
            }
            CatMood.Crying -> {
                // Big watery eye with a huge highlight and a fat tear
                drawCircle(Ink, r * 0.2f, eye)
                drawCircle(Color.White, r * 0.08f, Offset(eye.x - r * 0.06f, eye.y - r * 0.07f))
                drawCircle(Color.White, r * 0.04f, Offset(eye.x + r * 0.07f, eye.y + r * 0.06f))
                drawArc(
                    Tear.copy(alpha = 0.7f), startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(eye.x - r * 0.24f, eye.y - r * 0.2f), size = Size(r * 0.48f, r * 0.48f),
                    style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
                )
                drawOval(
                    Tear,
                    topLeft = Offset(eye.x + side * r * 0.12f - r * 0.06f, eye.y + r * 0.22f),
                    size = Size(r * 0.12f, r * 0.2f),
                )
            }
        }
    }
}

private fun DrawScope.drawMouth(center: Offset, r: Float, mood: CatMood, time: Float) {
    val stroke = Stroke(width = r * 0.05f, cap = StrokeCap.Round)
    when (mood) {
        CatMood.Shocked -> drawOval(
            Ink,
            topLeft = Offset(center.x - r * 0.09f, center.y + r * 0.32f),
            size = Size(r * 0.18f, r * 0.2f),
        )
        CatMood.Crying -> {
            val wobble = sin(time * 20f) * r * 0.02f
            val frown = Path().apply {
                moveTo(center.x - r * 0.16f, center.y + r * 0.46f + wobble)
                quadraticTo(center.x, center.y + r * 0.3f, center.x + r * 0.16f, center.y + r * 0.46f - wobble)
            }
            drawPath(frown, Ink, style = stroke)
        }
        CatMood.Smug -> {
            val w = Path().apply {
                moveTo(center.x - r * 0.18f, center.y + r * 0.3f)
                quadraticTo(center.x - r * 0.09f, center.y + r * 0.42f, center.x, center.y + r * 0.28f)
                quadraticTo(center.x + r * 0.09f, center.y + r * 0.42f, center.x + r * 0.18f, center.y + r * 0.3f)
            }
            drawPath(w, Ink, style = stroke)
        }
    }
}
