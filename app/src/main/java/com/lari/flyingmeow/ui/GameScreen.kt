package com.lari.flyingmeow.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lari.flyingmeow.game.GameModel
import com.lari.flyingmeow.game.Phase
import kotlin.math.sin

private const val PREFS = "flying_meow"
private const val KEY_BEST = "best"
private const val KEY_SKIN = "skin"

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    val model = remember { GameModel().apply { restoreBest(prefs.getInt(KEY_BEST, 0)) } }
    var skin by remember {
        mutableStateOf(CatSkin.entries.getOrElse(prefs.getInt(KEY_SKIN, 0)) { CatSkin.Ginger })
    }

    // These states only exist to tell Compose it needs to redraw.
    var frame by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(Phase.Ready) }

    // Game loop: one update per display frame
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                model.update(dt)
                if (model.phase != phase) {
                    if (model.phase == Phase.GameOver) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        prefs.edit().putInt(KEY_BEST, model.best).apply()
                    }
                    phase = model.phase
                }
                frame = now
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                model.resize(it.width.toFloat(), it.height.toFloat())
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (model.tap()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                phase = model.phase
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Reading `frame` here makes the Canvas redraw on every loop frame
            if (frame >= 0L) drawWorld(model, skin)
        }

        when (phase) {
            Phase.Ready -> ReadyOverlay(
                selected = skin,
                onSelect = {
                    skin = it
                    prefs.edit().putInt(KEY_SKIN, it.ordinal).apply()
                },
                best = model.best,
            )
            Phase.Playing -> ScoreText(model.score)
            Phase.GameOver -> GameOverOverlay(model.score, model.best, model.deathPhrase)
        }
    }
}

// ---------------------------------------------------------------- world

private val SkyTop = Color(0xFF8FD3FF)
private val SkyBottom = Color(0xFFFFE3F1)
private val Sisal = Color(0xFFD9B98B)
private val SisalLine = Color(0xFFB8925E)
private val Carpet = Color(0xFFB57BEE)
private val CarpetDark = Color(0xFF8A52C7)
private val Floor = Color(0xFFC48A5A)
private val FloorDark = Color(0xFF9C6638)

private fun DrawScope.drawWorld(model: GameModel, skin: CatSkin) {
    val w = size.width
    val h = size.height

    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyBottom), endY = model.groundTop))

    // Parallax clouds (move slower than the scratchers)
    for (i in 0 until 4) {
        val cloudSpeed = model.speed * 0.15f
        val span = w + w * 0.6f
        val x = w - ((model.time * cloudSpeed + i * span / 4f) % span)
        val y = h * (0.12f + 0.09f * (i % 3))
        drawCloud(Offset(x, y), h * 0.035f)
    }

    // Cat scratchers instead of pipes
    model.pipes.forEach { p ->
        val gapTop = p.gapCenterY - model.gapSize / 2f
        val gapBottom = p.gapCenterY + model.gapSize / 2f
        drawScratcher(p.x, model.pipeWidth, 0f, gapTop, model.capHeight, capAtBottom = true)
        drawScratcher(p.x, model.pipeWidth, gapBottom, model.groundTop, model.capHeight, capAtBottom = false)
    }

    // Scrolling wooden floor
    drawRect(Floor, Offset(0f, model.groundTop), Size(w, h - model.groundTop))
    drawRect(FloorDark, Offset(0f, model.groundTop), Size(w, h * 0.008f))
    val plank = w * 0.22f
    var px = -model.groundOffset % plank
    while (px < w) {
        drawLine(FloorDark, Offset(px, model.groundTop), Offset(px - plank * 0.3f, h), strokeWidth = h * 0.004f)
        px += plank
    }

    val mood = when {
        model.phase == Phase.GameOver -> CatMood.Crying
        model.phase == Phase.Playing && model.timeSinceFlap < 0.25f -> CatMood.Shocked
        else -> CatMood.Smug
    }
    drawCat(
        center = Offset(model.catX, model.catY),
        r = model.catRadius,
        rotation = model.catRotation,
        skin = skin,
        mood = mood,
        time = model.time,
        timeSinceFlap = model.timeSinceFlap,
    )
}

private fun DrawScope.drawCloud(c: Offset, r: Float) {
    val white = Color.White.copy(alpha = 0.85f)
    drawCircle(white, r, c)
    drawCircle(white, r * 1.3f, Offset(c.x + r * 1.2f, c.y - r * 0.4f))
    drawCircle(white, r, Offset(c.x + r * 2.5f, c.y))
    drawRoundRect(white, Offset(c.x, c.y - r * 0.2f), Size(r * 2.5f, r * 1.2f), CornerRadius(r))
}

/** A sisal post with the padded platform facing the gap. */
private fun DrawScope.drawScratcher(
    x: Float, width: Float, top: Float, bottom: Float, capH: Float, capAtBottom: Boolean,
) {
    if (bottom <= top) return
    val colLeft = x + width * 0.1f
    val colW = width * 0.8f
    val colTop = if (capAtBottom) top else top + capH
    val colBottom = if (capAtBottom) bottom - capH else bottom

    if (colBottom > colTop) {
        drawRect(Sisal, Offset(colLeft, colTop), Size(colW, colBottom - colTop))
        val step = capH * 0.45f
        var y = colTop + step / 2f
        while (y < colBottom) {
            drawLine(SisalLine, Offset(colLeft, y), Offset(colLeft + colW, y + step * 0.3f), strokeWidth = step * 0.25f)
            y += step
        }
        drawRect(Color.Black.copy(alpha = 0.08f), Offset(colLeft + colW * 0.75f, colTop), Size(colW * 0.25f, colBottom - colTop))
    }

    val capTop = if (capAtBottom) bottom - capH else top
    drawRoundRect(Carpet, Offset(x, capTop), Size(width, capH), CornerRadius(capH * 0.35f))
    drawRoundRect(
        CarpetDark, Offset(x, capTop + capH * 0.7f), Size(width, capH * 0.3f), CornerRadius(capH * 0.15f),
    )

    // Yarn ball hanging from the bottom platform, just because
    if (!capAtBottom) {
        val bx = x + width * 0.78f
        val by = capTop - capH * 0.55f
        drawCircle(Color(0xFFFF6B8A), capH * 0.45f, Offset(bx, by))
        drawLine(Color(0xFFD94A6A), Offset(bx - capH * 0.3f, by - capH * 0.2f), Offset(bx + capH * 0.3f, by + capH * 0.2f), strokeWidth = capH * 0.08f)
    }
}

// ---------------------------------------------------------------- overlay UI

private val Outline = Shadow(Color(0xFF3A2350), Offset(0f, 6f), blurRadius = 0f)

private fun titleStyle(size: Int) = TextStyle(
    color = Color.White,
    fontSize = size.sp,
    fontWeight = FontWeight.Black,
    textAlign = TextAlign.Center,
    shadow = Outline,
)

@Composable
private fun ScoreText(score: Int) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
        Text(score.toString(), style = titleStyle(64))
    }
}

@Composable
private fun ReadyOverlay(selected: CatSkin, onSelect: (CatSkin) -> Unit, best: Int) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Flying\nMeow", style = titleStyle(56).copy(lineHeight = 56.sp))
        if (best > 0) Text("best: $best", style = titleStyle(20))
        Spacer(Modifier.weight(1f))
        Text("tap to fly", style = titleStyle(24).copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CatSkin.entries.forEach { s -> SkinChip(s, s == selected) { onSelect(s) } }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun SkinChip(skin: CatSkin, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.9f else 0.5f))
            .border(3.dp, if (selected) Color(0xFF8A52C7) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(44.dp)) {
            drawCat(
                center = Offset(size.width / 2f, size.height * 0.58f),
                r = size.width * 0.3f,
                rotation = 0f,
                skin = skin,
                mood = CatMood.Smug,
                time = 0f,
                timeSinceFlap = 99f,
            )
        }
        Text(skin.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2350))
    }
}

@Composable
private fun GameOverOverlay(score: Int, best: Int, phrase: String) {
    val bob = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { bob.floatValue = sin((it - start) / 2.5e8f) * 4f }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .offset(y = bob.floatValue.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFF6FB))
                .border(4.dp, Color(0xFF3A2350), RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("game over", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A2350))
            Text("\"$phrase\"", fontSize = 16.sp, color = Color(0xFF8A52C7), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Stat("score", score)
                Stat("best", best)
            }
            if (score > 0 && score >= best) {
                Spacer(Modifier.height(8.dp))
                Text("new best! 🏆", fontWeight = FontWeight.Bold, color = Color(0xFFE07A00))
            }
            Spacer(Modifier.height(16.dp))
            Text("tap to try again", fontSize = 14.sp, color = Color(0xFF6B5A7A))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = Color(0xFF6B5A7A))
        Text(value.toString(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A2350))
    }
}
