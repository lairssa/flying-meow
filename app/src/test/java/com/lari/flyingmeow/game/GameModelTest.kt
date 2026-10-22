package com.lari.flyingmeow.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class GameModelTest {

    private lateinit var game: GameModel
    private val frame = 1f / 60f

    @Before
    fun setUp() {
        game = GameModel(Random(42))
        game.resize(1080f, 2000f)
    }

    private fun run(seconds: Float, flapEvery: Float? = null) {
        var t = 0f
        var sinceFlap = 0f
        while (t < seconds) {
            game.update(frame)
            t += frame
            sinceFlap += frame
            if (flapEvery != null && sinceFlap >= flapEvery && game.phase == Phase.Playing) {
                // Keeps the cat in the lower part of the next scratcher's gap
                val next = game.pipes.firstOrNull { it.x + game.pipeWidth > game.catX - game.catRadius }
                val target = next?.let { it.gapCenterY + game.gapSize * 0.2f } ?: (game.height * 0.45f)
                if (game.catY > target) game.tap()
                sinceFlap = 0f
            }
        }
    }

    @Test
    fun `starts idle waiting for a tap`() {
        val y = game.catY
        run(1f)
        assertEquals(Phase.Ready, game.phase)
        assertEquals(y, game.catY, game.height * 0.02f)
    }

    @Test
    fun `first tap starts the game and makes the cat rise`() {
        val y = game.catY
        assertTrue(game.tap())
        assertEquals(Phase.Playing, game.phase)
        game.update(frame)
        assertTrue(game.catY < y)
    }

    @Test
    fun `without flapping the cat falls and dies on the ground`() {
        game.tap()
        run(3f)
        assertEquals(Phase.GameOver, game.phase)
        assertEquals(game.groundTop - game.catRadius, game.catY, 1f)
    }

    @Test
    fun `passing scratchers scores points`() {
        game.tap()
        run(12f, flapEvery = 0.05f)
        assertTrue("expected to score, got ${game.score}", game.score >= 3)
    }

    @Test
    fun `best score is kept after dying`() {
        game.restoreBest(7)
        game.tap()
        run(3f)
        assertEquals(7, game.best)
    }

    @Test
    fun `restarts only after a short delay on game over`() {
        game.tap()
        run(3f)
        game.tap()
        assertEquals(Phase.Ready, game.phase)
    }

    @Test
    fun `circle and rectangle collision`() {
        assertTrue(circleHitsRect(5f, 5f, 2f, 0f, 0f, 10f, 10f))
        assertTrue(circleHitsRect(11f, 5f, 2f, 0f, 0f, 10f, 10f))
        assertFalse(circleHitsRect(13f, 5f, 2f, 0f, 0f, 10f, 10f))
        assertFalse(circleHitsRect(12f, 12f, 2f, 0f, 0f, 10f, 10f))
    }
}
