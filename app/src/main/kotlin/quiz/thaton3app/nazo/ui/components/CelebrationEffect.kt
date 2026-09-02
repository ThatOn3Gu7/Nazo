package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import android.graphics.Color as AndroidColor

// ---------------------------------------------------------------------------
// Celebration effects ("graffiti"): the end-of-game confetti, in switchable
// variants. Shared by QuizCompleteScreen and GuessingResultsScreen; the
// selected variant is a user preference (Appearance → Celebrations), and the
// Appearance sheet shows a live miniature preview of every variant via
// drawCelebrationPreview. Inspired by Image Toolbox's confetti presets.
// ---------------------------------------------------------------------------

data class CelebrationStyle(val id: String, val label: String, val blurb: String)

val CELEBRATION_STYLES = listOf(
    CelebrationStyle("none", "None", "No confetti — straight to the numbers"),
    CelebrationStyle("burst", "Classic Burst", "One big pop from the middle of the screen"),
    CelebrationStyle("festive", "Festive Cannon", "A fountain erupting from the bottom"),
    CelebrationStyle("rain", "Confetti Rain", "A gentle shower drifting down from the top"),
    CelebrationStyle("cannons", "Side Cannons", "Both bottom corners fire across the screen"),
    CelebrationStyle("fireworks", "Fireworks", "Staggered little pops all over the screen"),
)

/**
 * Confetti colors derived from the ACTIVE accent palette (owner's choice over
 * the old fixed rainbow): the accent hue plus analogous rotations and a couple
 * of lighter sparkle shades, so the celebration always matches the theme.
 */
fun celebrationPalette(): List<Color> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(NazoPrimary.toArgb(), hsv)
    fun shade(dHue: Float, satMul: Float, valMul: Float): Color {
        val out = floatArrayOf(
            (hsv[0] + dHue + 360f) % 360f,
            (hsv[1] * satMul).coerceIn(0.30f, 1f),
            (hsv[2] * valMul).coerceIn(0.45f, 1f),
        )
        return Color(AndroidColor.HSVToColor(out))
    }
    return listOf(
        shade(0f, 1.00f, 1.20f),
        shade(22f, 1.05f, 1.00f),
        shade(-22f, 1.05f, 1.00f),
        shade(45f, 0.85f, 1.25f),
        shade(-45f, 0.85f, 1.25f),
        shade(10f, 0.45f, 1.45f), // pale sparkle
    )
}

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, var vr: Float,
    var size: Float,
    var color: Color,
    var ttl: Float, // seconds of life remaining; alpha fades near zero
)

private fun MutableList<Particle>.spawn(
    x: Float, y: Float, vx: Float, vy: Float, size: Float, color: Color, ttl: Float, rng: Random,
) = add(Particle(x, y, vx, vy, rng.nextFloat() * 360f, (rng.nextFloat() - 0.5f) * 720f, size, color, ttl))

/**
 * Full-screen one-shot celebration overlay. Composes nothing for "none".
 * Runs its physics loop until every particle has died, then goes idle.
 */
@Composable
fun CelebrationOverlay(style: String, modifier: Modifier = Modifier) {
    if (style == "none" || CELEBRATION_STYLES.none { it.id == style }) return

    val particles = remember { mutableStateListOf<Particle>() }
    val frame = remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val colors = celebrationPalette()

    LaunchedEffect(style, canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        particles.clear()
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val rng = Random(System.nanoTime())

        // Per-style physics: emission window (s), gravity (px/s²), air drag (1/s).
        val emitEnd: Float
        val gravity: Float
        val drag: Float
        when (style) {
            "festive" -> { emitEnd = 1.3f; gravity = 2000f; drag = 0.35f }
            "rain" -> { emitEnd = 3.0f; gravity = 150f; drag = 0f }
            "cannons" -> { emitEnd = 1.1f; gravity = 1900f; drag = 0.30f }
            "fireworks" -> { emitEnd = 1.5f; gravity = 650f; drag = 1.6f }
            else -> { emitEnd = 0f; gravity = 1800f; drag = 0f } // burst: instant pop
        }

        // Fireworks pop schedule: five staggered mini-explosions.
        val popTimes = floatArrayOf(0f, 0.35f, 0.7f, 1.05f, 1.4f)
        var nextPop = 0

        // Classic burst: everything at once, from the upper-middle.
        if (style == "burst") {
            repeat(70) {
                particles.spawn(
                    x = w * 0.5f, y = h / 3f,
                    vx = rng.nextFloat() * 1200f - 600f,
                    vy = -(rng.nextFloat() * 1000f + 400f),
                    size = rng.nextFloat() * 20f + 10f,
                    color = colors.random(rng), ttl = 5f, rng = rng,
                )
            }
        }

        var elapsed = 0f
        var spawnDebt = 0f
        var lastFrameTime = withFrameNanos { it }
        while (isActive && (particles.isNotEmpty() || elapsed <= emitEnd)) {
            withFrameNanos { frameTime ->
                val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrameTime = frameTime
                elapsed += dt

                // --- Emit ---
                if (elapsed <= emitEnd) when (style) {
                    "festive" -> {
                        spawnDebt += 110f * dt
                        while (spawnDebt >= 1f) {
                            spawnDebt -= 1f
                            val ang = (-90f + (rng.nextFloat() - 0.5f) * 70f) * (PI.toFloat() / 180f)
                            val speed = 2000f + rng.nextFloat() * 1300f
                            particles.spawn(
                                x = w * 0.5f + (rng.nextFloat() - 0.5f) * w * 0.10f, y = h + 10f,
                                vx = cos(ang) * speed, vy = sin(ang) * speed,
                                size = rng.nextFloat() * 14f + 8f,
                                color = colors.random(rng), ttl = 4f, rng = rng,
                            )
                        }
                    }
                    "rain" -> {
                        spawnDebt += 80f * dt
                        while (spawnDebt >= 1f) {
                            spawnDebt -= 1f
                            particles.spawn(
                                x = rng.nextFloat() * w, y = -30f,
                                vx = (rng.nextFloat() - 0.5f) * 160f,
                                vy = 200f + rng.nextFloat() * 300f,
                                size = rng.nextFloat() * 14f + 8f,
                                color = colors.random(rng), ttl = 8f, rng = rng,
                            )
                        }
                    }
                    "cannons" -> {
                        spawnDebt += 90f * dt
                        while (spawnDebt >= 1f) {
                            spawnDebt -= 1f
                            val fromLeft = rng.nextBoolean()
                            val speedX = 500f + rng.nextFloat() * 700f
                            particles.spawn(
                                x = if (fromLeft) -10f else w + 10f, y = h * (0.85f + rng.nextFloat() * 0.1f),
                                vx = if (fromLeft) speedX else -speedX,
                                vy = -(1500f + rng.nextFloat() * 900f),
                                size = rng.nextFloat() * 14f + 8f,
                                color = colors.random(rng), ttl = 4f, rng = rng,
                            )
                        }
                    }
                    "fireworks" -> {
                        while (nextPop < popTimes.size && elapsed >= popTimes[nextPop]) {
                            nextPop++
                            val cx = w * (0.2f + rng.nextFloat() * 0.6f)
                            val cy = h * (0.15f + rng.nextFloat() * 0.35f)
                            val popColors = listOf(colors.random(rng), colors.random(rng))
                            repeat(28) {
                                val ang = rng.nextFloat() * 2f * PI.toFloat()
                                val speed = 300f + rng.nextFloat() * 700f
                                particles.spawn(
                                    x = cx, y = cy,
                                    vx = cos(ang) * speed, vy = sin(ang) * speed,
                                    size = rng.nextFloat() * 9f + 5f,
                                    color = popColors.random(rng),
                                    ttl = 1.2f + rng.nextFloat() * 0.6f, rng = rng,
                                )
                            }
                        }
                    }
                }

                // --- Integrate ---
                val decay = exp(-drag * dt)
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.vy += gravity * dt
                    p.vx *= decay
                    p.vy *= decay
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rot += p.vr * dt
                    p.ttl -= dt
                    if (p.ttl <= 0f || p.y > h + 60f) iterator.remove()
                }
            }
            frame.intValue++ // force Canvas redraw each frame
        }
    }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        // Reading `frame` inside the draw phase invalidates only the draw
        // layer each physics tick (same trick as the old ConfettiBurst).
        @Suppress("UNUSED_VARIABLE")
        val currentFrame = frame.intValue
        particles.forEach { p ->
            val alpha = (p.ttl / 0.6f).coerceIn(0f, 1f) // fade out over the last 0.6s
            rotate(degrees = p.rot, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(p.x - p.size / 2f, p.y - p.size * 0.3f),
                    size = Size(p.size, p.size * 0.6f),
                    alpha = alpha,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Miniature looping previews for the Appearance sheet's option cards.
// Deterministic closed-form ballistics (no particle state): every card can
// loop forever off the sheet's shared clock without allocations.
// ---------------------------------------------------------------------------

/** Cheap deterministic pseudo-random in [0,1) from an index + salt. */
private fun hash(i: Int, salt: Float): Float {
    val v = sin(i * 127.1f + salt * 311.7f) * 43758.547f
    return v - kotlin.math.floor(v)
}

/**
 * Draws a looping miniature of celebration [style] scaled to the card.
 * [t] is the sheet's unbounded clock where 1.0 == 30s (same convention as
 * the background-effects previews).
 */
fun DrawScope.drawCelebrationPreview(style: String, t: Float) {
    if (style == "none") return
    val w = size.width
    val h = size.height
    val colors = celebrationPalette()
    val seconds = t * 30f
    val period = 2.6f
    val tau = seconds % period
    val fadeIn = (tau / 0.1f).coerceIn(0f, 1f)
    val fadeOut = ((period - tau) / 0.45f).coerceIn(0f, 1f)
    val g = h * 4.5f // preview gravity, px/s²

    fun confetto(x: Float, y: Float, i: Int, alpha: Float, scale: Float = 1f) {
        if (alpha <= 0.01f || y > h + 8f) return
        val s = (h * 0.055f + hash(i, 9.1f) * h * 0.045f) * scale
        rotate(degrees = hash(i, 3.3f) * 360f + tau * (120f + hash(i, 4.4f) * 240f), pivot = Offset(x, y)) {
            drawRect(
                color = colors[i % colors.size],
                topLeft = Offset(x - s / 2f, y - s * 0.3f),
                size = Size(s, s * 0.6f),
                alpha = alpha.coerceAtMost(0.85f),
            )
        }
    }

    when (style) {
        "burst" -> {
            // One pop from the card centre, restarting every cycle.
            repeat(18) { i ->
                val vx = (hash(i, 1.2f) - 0.5f) * w * 1.1f
                val vy = -(0.4f + hash(i, 2.5f) * 0.75f) * h * 2.6f
                val x = w * 0.5f + vx * tau
                val y = h * 0.42f + vy * tau + 0.5f * g * tau * tau
                confetto(x, y, i, fadeIn * fadeOut)
            }
        }
        "festive" -> {
            // Fountain from the bottom centre; staggered births.
            repeat(20) { i ->
                val age = tau - hash(i, 5.7f) * 1.1f
                if (age < 0f) return@repeat
                val ang = (-90f + (hash(i, 1.9f) - 0.5f) * 70f) * (PI.toFloat() / 180f)
                val speed = h * (2.2f + hash(i, 6.2f) * 1.3f)
                val x = w * 0.5f + cos(ang) * speed * age
                val y = h + sin(ang) * speed * age + 0.5f * g * age * age
                confetto(x, y, i, fadeOut)
            }
        }
        "rain" -> {
            // Seamless shower: particles wrap top-to-bottom, no cycle seam.
            repeat(16) { i ->
                val speed = 0.35f + hash(i, 7.7f) * 0.4f // card-heights per second
                val y = ((hash(i, 2.2f) + seconds * speed) % 1.15f - 0.075f) * h
                val x = (hash(i, 8.4f) + sin(seconds * 1.7f + i) * 0.02f) * w
                confetto(x, y, i, 0.8f)
            }
        }
        "cannons" -> {
            // Two bottom-corner cannons firing inward; staggered births.
            repeat(20) { i ->
                val age = tau - hash(i, 5.1f) * 0.9f
                if (age < 0f) return@repeat
                val fromLeft = i % 2 == 0
                val vx = w * (0.55f + hash(i, 3.9f) * 0.65f) * (if (fromLeft) 1f else -1f)
                val vy = -h * (2.0f + hash(i, 6.8f) * 1.2f)
                val x = (if (fromLeft) 0f else w) + vx * age
                val y = h * 0.95f + vy * age + 0.5f * g * age * age
                confetto(x, y, i, fadeOut)
            }
        }
        "fireworks" -> {
            // Three staggered pops across the card.
            val popX = floatArrayOf(0.25f, 0.72f, 0.48f)
            val popY = floatArrayOf(0.38f, 0.30f, 0.55f)
            val popT = floatArrayOf(0.15f, 0.95f, 1.75f)
            for (p in 0..2) {
                val age = tau - popT[p]
                if (age < 0f || age > 0.9f) continue
                val alpha = 1f - age / 0.9f
                repeat(10) { i ->
                    val idx = p * 10 + i
                    val ang = (i / 10f + hash(idx, 4.7f) * 0.08f) * 2f * PI.toFloat()
                    val speed = h * (0.8f + hash(idx, 2.8f) * 1.0f)
                    // Mild drag: velocity eases off as the pop expands.
                    val dist = speed * (1f - exp(-2.2f * age)) / 2.2f
                    val x = popX[p] * w + cos(ang) * dist
                    val y = popY[p] * h + sin(ang) * dist + 0.5f * (g * 0.25f) * age * age
                    confetto(x, y, idx, alpha, scale = 0.75f)
                }
            }
        }
    }
}
