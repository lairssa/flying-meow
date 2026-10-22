package com.lari.flyingmeow.game

import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class Phase { Ready, Playing, GameOver }

class Pipe(var x: Float, val gapCenterY: Float) {
    var scored: Boolean = false
}

private val DEATH_PHRASES = listOf(
    "it was the wind, I swear",
    "nine lives, minus one",
    "meow? MEOW?!",
    "fell, but with style",
    "don't care (I do care)",
    "that scratcher was crooked",
    "knocking your glass off the table for this",
)

/**
 * All the game logic in pure Kotlin (no Android), so it's easy to test.
 * Sizes are proportional to the screen, so the game plays the same on any phone.
 */
class GameModel(private val random: Random = Random.Default) {

    var width = 0f; private set
    var height = 0f; private set

    var phase = Phase.Ready; private set
    var catY = 0f; private set
    var catVelocity = 0f; private set
    var score = 0; private set
    var best = 0; private set
    var deathPhrase = ""; private set
    var flapCount = 0; private set

    var time = 0f; private set
    var timeSinceFlap = 99f; private set
    var timeSinceDeath = 0f; private set
    var groundOffset = 0f; private set

    private val _pipes = mutableListOf<Pipe>()
    val pipes: List<Pipe> get() = _pipes

    // World measurements
    val catX get() = width * 0.3f
    val catRadius get() = height * 0.035f
    val pipeWidth get() = width * 0.18f
    val gapSize get() = height * 0.28f
    val capHeight get() = height * 0.035f
    val groundTop get() = height * 0.9f
    val speed get() = width * 0.45f

    // Physics
    private val gravity get() = height * 2.4f
    private val flapVelocity get() = -height * 0.7f
    private val pipeSpacing get() = width * 0.62f

    /** Cat tilt in degrees: nose up while rising, nose down while falling. */
    val catRotation: Float
        get() = if (phase == Phase.Ready || height == 0f) 0f
        else (catVelocity / height * 40f).coerceIn(-25f, 75f)

    fun restoreBest(value: Int) {
        best = max(best, value)
    }

    fun resize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f || (w == width && h == height)) return
        width = w
        height = h
        reset()
    }

    fun reset() {
        phase = Phase.Ready
        catY = height * 0.45f
        catVelocity = 0f
        score = 0
        _pipes.clear()
    }

    /** Returns true if the cat flapped its paws (handy for vibrating the phone). */
    fun tap(): Boolean = when (phase) {
        Phase.Ready -> { phase = Phase.Playing; flap(); true }
        Phase.Playing -> { flap(); true }
        Phase.GameOver -> { if (timeSinceDeath > 0.6f) reset(); false }
    }

    private fun flap() {
        catVelocity = flapVelocity
        timeSinceFlap = 0f
        flapCount++
    }

    fun update(rawDt: Float) {
        if (width == 0f) return
        val dt = rawDt.coerceIn(0f, 1f / 30f) // avoids "teleporting" if a frame runs late
        time += dt
        timeSinceFlap += dt

        when (phase) {
            Phase.Ready -> {
                catY = height * 0.45f + sin(time * 4f) * height * 0.015f
                scrollGround(dt)
            }
            Phase.Playing -> {
                catVelocity += gravity * dt
                catY += catVelocity * dt
                if (catY < catRadius) { catY = catRadius; catVelocity = 0f }
                scrollGround(dt)
                updatePipes(dt)
                if (hitSomething()) die()
            }
            Phase.GameOver -> {
                timeSinceDeath += dt
                val floor = groundTop - catRadius
                if (catY < floor) {
                    catVelocity += gravity * dt
                    catY = (catY + catVelocity * dt).coerceAtMost(floor)
                } else {
                    catY = floor // the death frame may have overshot into the floor
                }
            }
        }
    }

    private fun scrollGround(dt: Float) {
        groundOffset = (groundOffset + speed * dt) % width
    }

    private fun updatePipes(dt: Float) {
        _pipes.forEach { it.x -= speed * dt }
        _pipes.removeAll { it.x + pipeWidth < 0f }

        val last = _pipes.lastOrNull()
        when {
            last == null -> _pipes += Pipe(width * 1.3f, randomGapCenter())
            last.x + pipeSpacing <= width + pipeWidth -> _pipes += Pipe(last.x + pipeSpacing, randomGapCenter())
        }

        _pipes.forEach {
            if (!it.scored && it.x + pipeWidth < catX - catRadius) {
                it.scored = true
                score++
            }
        }
    }

    private fun randomGapCenter(): Float {
        val margin = gapSize / 2f + height * 0.08f
        val min = margin
        val max = groundTop - margin
        return min + random.nextFloat() * (max - min)
    }

    private fun hitSomething(): Boolean {
        val r = catRadius * 0.85f // slightly forgiving hitbox
        if (catY + r >= groundTop) return true
        val cx = catX
        val cy = catY
        return _pipes.any { p ->
            val gapTop = p.gapCenterY - gapSize / 2f
            val gapBottom = p.gapCenterY + gapSize / 2f
            val colLeft = p.x + pipeWidth * 0.1f
            val colRight = p.x + pipeWidth * 0.9f
            circleHitsRect(cx, cy, r, colLeft, 0f, colRight, gapTop - capHeight) ||
                circleHitsRect(cx, cy, r, p.x, gapTop - capHeight, p.x + pipeWidth, gapTop) ||
                circleHitsRect(cx, cy, r, p.x, gapBottom, p.x + pipeWidth, gapBottom + capHeight) ||
                circleHitsRect(cx, cy, r, colLeft, gapBottom + capHeight, colRight, groundTop)
        }
    }

    private fun die() {
        phase = Phase.GameOver
        timeSinceDeath = 0f
        best = max(best, score)
        deathPhrase = DEATH_PHRASES.random(random)
        if (catVelocity < 0f) catVelocity = 0f
    }
}

internal fun circleHitsRect(
    cx: Float, cy: Float, r: Float,
    left: Float, top: Float, right: Float, bottom: Float,
): Boolean {
    if (right <= left || bottom <= top) return false
    val nx = cx.coerceIn(left, right)
    val ny = cy.coerceIn(top, bottom)
    val dx = cx - nx
    val dy = cy - ny
    return dx * dx + dy * dy < r * r
}
