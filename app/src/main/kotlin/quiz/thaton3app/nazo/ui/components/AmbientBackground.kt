package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess

private enum class ParticleShape { CIRCLE, SQUARE, TRIANGLE, PENTAGON, STAR, HEXAGON, DIAMOND }

private data class ShapeParticle(
    val baseX: Float,
    val baseY: Float,
    val size: Float,
    val shape: ParticleShape,
    val driftFreqX: Float,
    val driftFreqY: Float,
    val phaseX: Float,
    val phaseY: Float,
    val ampX: Float,
    val ampY: Float,
    val rotSpeed: Float,
    val baseAlpha: Float,
)

private data class ConstellationStar(
    val fracX: Float,
    val fracY: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val pulseFreq: Float,
    val pulsePhase: Float,
)

private data class RainDrop(
    val fracX: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float,
)

private data class GlowingOrb(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val driftFreq: Float,
    val phase: Float,
    val amp: Float,
    val color: Color,
)

private class ParticleDrawCache {
    var builtForMinDimension = -1f
        private set

    val paths = arrayOfNulls<Path>(ParticleShape.values().size)
    var stroke: Stroke = Stroke()
    var circleRadius = 0f

    fun ensure(minDimension: Float, particleSize: Float) {
        if (minDimension == builtForMinDimension) return
        builtForMinDimension = minDimension

        val r = particleSize * minDimension
        stroke = Stroke(
            width = (r * 0.08f).coerceAtLeast(3f),
            pathEffect = PathEffect.cornerPathEffect(r * 0.35f),
        )
        circleRadius = r * 0.9f

        paths[ParticleShape.SQUARE.ordinal] = Path().apply {
            val sr = r * 0.8f
            moveTo(-sr, -sr); lineTo(sr, -sr); lineTo(sr, sr); lineTo(-sr, sr); close()
        }
        paths[ParticleShape.TRIANGLE.ordinal] = Path().apply {
            val tr = r * 1.15f
            val hDist = tr * 0.866f
            val vDist = tr * 0.5f
            moveTo(0f, -tr); lineTo(hDist, vDist); lineTo(-hDist, vDist); close()
        }
        paths[ParticleShape.PENTAGON.ordinal] = regularPolygon(r * 1.05f, 5)
        paths[ParticleShape.STAR.ordinal] = Path().apply {
            val str = r * 1.3f
            val points = 5
            for (i in 0 until (points * 2)) {
                val angle = (i * PI / points).toFloat() - (PI / 2).toFloat()
                val currentR = if (i % 2 == 0) str else str * 0.45f
                val px = currentR * cos(angle)
                val py = currentR * sin(angle)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        paths[ParticleShape.HEXAGON.ordinal] = regularPolygon(r * 1.0f, 6)
        paths[ParticleShape.DIAMOND.ordinal] = Path().apply {
            val dr = r * 1.15f
            moveTo(0f, -dr); lineTo(dr, 0f); lineTo(0f, dr); lineTo(-dr, 0f); close()
        }
    }

    private fun regularPolygon(radius: Float, sides: Int): Path = Path().apply {
        for (i in 0 until sides) {
            val angle = (i * 2 * PI / sides).toFloat() - (PI / 2).toFloat()
            val px = radius * cos(angle)
            val py = radius * sin(angle)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

// ---------------------------------------------------------------------------
// Entrance ("bloom") / exit transition support
// ---------------------------------------------------------------------------
// When the style changes, the incoming variant doesn't just appear: it blooms
// in with choreography that suits its character (constellation web grows out
// of a small centre web, orbs pop in small then spring up to size, shapes
// scale in with a twist, rain falls in from above the screen), while the
// outgoing variant fades away briefly instead of hard-cutting. All progress
// values are derived in the DRAW phase from the same unbounded frame clock
// that animates the styles — no extra animations, no recomposition.

/** Per-style bloom durations, in clock cycles (1 cycle = 30 s). */
private const val BLOOM_SHAPES_CYCLES = 1.8f / 30f
private const val BLOOM_CONSTELLATION_CYCLES = 2.4f / 30f
private const val BLOOM_RAIN_CYCLES = 1.7f / 30f
private const val BLOOM_ORBS_CYCLES = 2.0f / 30f

/** How long the outgoing style takes to fade away, in cycles. */
private const val FADE_OUT_CYCLES = 0.45f / 30f

private fun bloomCyclesFor(style: String): Float = when (style) {
    "constellation" -> BLOOM_CONSTELLATION_CYCLES
    "rain" -> BLOOM_RAIN_CYCLES
    "orbs" -> BLOOM_ORBS_CYCLES
    else -> BLOOM_SHAPES_CYCLES
}

private fun easeOutCubic(p: Float): Float {
    val q = 1f - p
    return 1f - q * q * q
}

/** Ease-out with a slight overshoot past 1 — the springy "pop" feel. */
private fun easeOutBack(p: Float): Float {
    val s = 1.70158f
    val q = p - 1f
    return 1f + (s + 1f) * q * q * q + s * q * q
}

/**
 * Staggered sub-progress: element [index] waits its (shuffled) turn inside the
 * shared 0..1 [appear] timeline. [window] is the fraction of the timeline used
 * for staggering starts; every element still finishes by appear == 1.
 */
private fun stagger(appear: Float, index: Int, count: Int, window: Float): Float {
    val order = ((index * 13) % count).toFloat() / count
    return ((appear - order * window) / (1f - window)).coerceIn(0f, 1f)
}

/**
 * Plain (non-snapshot) bookkeeping for the style transition. Composition
 * writes to it on style changes and the frame loop updates [clock]; nothing
 * here is observable state, so touching it never schedules recomposition.
 * UI-thread only.
 */
private class BackgroundTransition {
    /** Style currently blooming in / steady ("none" if the user opted out). */
    var shown: String = "none"

    /** Previous style still fading out, or null once it has finished. */
    var retiring: String? = null

    /** Clock timestamps (in cycles) for the bloom and the fade-out. */
    var bloomStart = 0f
    var retireStart = 0f

    /** Latest clock value, mirrored from the frame loop as a plain var. */
    var clock = 0f
}

/**
 * Enhanced ambient background supporting multiple distinct visual variants
 * (Floating Shapes, Constellation Web, Digital Rain, Glowing Orbs) plus an
 * optional interactive touch ripple / sparkle burst system.
 * `style = "none"` disables the effect entirely.
 *
 * Style switches are choreographed: the new variant blooms in (in a manner
 * suited to its style) while the old one fades away. After a switch to
 * "none" completes its fade-out, the frame loop shuts itself down and the
 * composable composes nothing — zero per-frame work, exactly as before.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    style: String = "shapes", // "none" | "shapes" | "constellation" | "rain" | "orbs"
) {
    val trans = remember { BackgroundTransition() }
    // Whether the canvas + frame clock should exist at all. Starts true for a
    // real style, false for "none". The frame loop flips it back to false
    // once a fade-to-none has finished, which tears everything down.
    var engineActive by remember { mutableStateOf(style != "none") }

    if (trans.shown != style) {
        trans.retiring = trans.shown.takeIf { it != "none" }
        trans.retireStart = trans.clock
        trans.shown = style
        trans.bloomStart = trans.clock
        if (style != "none" || trans.retiring != null) engineActive = true
    }

    // "None" with nothing left to fade out: compose nothing (no canvas, no
    // frame clock, zero per-frame work).
    if (!engineActive) return

    val shapeParticles = remember { buildShapeParticles() }
    val constellationStars = remember { buildConstellationStars() }
    val rainDrops = remember { buildRainDrops() }
    val glowingOrbs = remember { buildGlowingOrbs() }
    val cache = remember { ParticleDrawCache() }

    // Unbounded, monotonic animation clock (in "cycles": 1.0 = the old 30s
    // sweep, so every existing speed constant below keeps its exact pace).
    // A clock that only ever counts UP has no wrap point: the sin/cos and
    // modulo-based formulas are all continuous in t, making every background
    // a true endless loop. Keyed on engineActive so that if the engine is
    // ever restarted within the same composition, the loop relaunches;
    // trans.clock carries the time domain across relaunches so bloom/fade
    // timestamps stay valid.
    var timeCycles by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engineActive) {
        if (!engineActive) return@LaunchedEffect
        val baseCycles = trans.clock
        val startNanos = withFrameNanos { it }
        while (true) {
            val shutDown = withFrameNanos { now ->
                val t = baseCycles + (now - startNanos) / 30_000_000_000f
                trans.clock = t
                timeCycles = t
                // Once a switch to "none" has fully faded out, stop burning
                // frames (the composable then composes nothing at all).
                trans.shown == "none" && (t - trans.retireStart) > FADE_OUT_CYCLES * 1.5f
            }
            if (shutDown) {
                engineActive = false
                break
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = timeCycles

        // Outgoing style: brief graceful fade instead of a hard cut.
        val retiring = trans.retiring
        if (retiring != null) {
            val fadeP = (t - trans.retireStart) / FADE_OUT_CYCLES
            if (fadeP >= 1f) {
                trans.retiring = null
            } else {
                val master = 1f - easeOutCubic(fadeP.coerceIn(0f, 1f))
                drawAmbientStyle(
                    retiring, t, appear = 1f, master = master,
                    shapeParticles, constellationStars, rainDrops, glowingOrbs, cache,
                )
            }
        }

        // Incoming / steady style, blooming from 0 to 1 after every switch
        // (including the very first appearance).
        if (trans.shown != "none") {
            val appear = ((t - trans.bloomStart) / bloomCyclesFor(trans.shown)).coerceIn(0f, 1f)
            drawAmbientStyle(
                trans.shown, t, appear = appear, master = 1f,
                shapeParticles, constellationStars, rainDrops, glowingOrbs, cache,
            )
        }
    }
}

/**
 * Draws one ambient style. [appear] is the bloom progress (0 = just switched
 * to, 1 = fully formed; every formula reduces to the classic steady-state
 * rendering at 1). [master] is a global alpha multiplier used to fade the
 * outgoing style during a switch.
 */
private fun DrawScope.drawAmbientStyle(
    style: String,
    t: Float,
    appear: Float,
    master: Float,
    shapeParticles: List<ShapeParticle>,
    constellationStars: List<ConstellationStar>,
    rainDrops: List<RainDrop>,
    glowingOrbs: List<GlowingOrb>,
    cache: ParticleDrawCache,
) {
    when (style) {
        "constellation" -> drawConstellationWeb(constellationStars, t, appear, master)
        "rain" -> drawDigitalRain(rainDrops, t, appear, master)
        "orbs" -> drawGlowingOrbsStyle(glowingOrbs, t, appear, master)
        else -> drawFloatingShapes(shapeParticles, cache, t, appear, master)
    }
}

private fun DrawScope.drawConstellationWeb(
    stars: List<ConstellationStar>,
    t: Float,
    appear: Float,
    master: Float,
) {
    // --- CONSTELLATION WEB STYLE ---
    // Bloom: the web starts as a SMALL web near the centre of the screen
    // (~22% spread) and grows outward to full size, stars popping in one
    // after another and the links strengthening as it expands.
    val w = size.width
    val h = size.height
    val minDimension = if (w < h) w else h
    val maxDist = minDimension * 0.45f

    val growth = easeOutCubic(appear)
    val spread = 0.22f + 0.78f * growth
    val lineStrength = 0.5f + 0.5f * growth
    val cx0 = w * 0.5f
    val cy0 = h * 0.5f

    // Per-star staggered entrance progress (eased) and screen positions.
    // At appear == 1 spread == 1 and every entry == 1, i.e. classic rendering.
    val entrance = FloatArray(stars.size)
    val starPositions = ArrayList<Offset>(stars.size)
    for (i in stars.indices) {
        val s = stars[i]
        entrance[i] = easeOutCubic(stagger(appear, i, stars.size, 0.55f))
        val xFull = (s.fracX * w + t * s.vx * w * 1.5f).let { ((it % w) + w) % w }
        val yFull = (s.fracY * h + t * s.vy * h * 1.5f).let { ((it % h) + h) % h }
        starPositions.add(Offset(cx0 + (xFull - cx0) * spread, cy0 + (yFull - cy0) * spread))
    }

    // Draw connecting lines first
    for (i in starPositions.indices) {
        if (entrance[i] <= 0f) continue
        for (j in i + 1 until starPositions.size) {
            if (entrance[j] <= 0f) continue
            val p1 = starPositions[i]
            val p2 = starPositions[j]
            val dist = hypot(p2.x - p1.x, p2.y - p1.y)
            if (dist < maxDist) {
                val pair = if (entrance[i] < entrance[j]) entrance[i] else entrance[j]
                val alpha = (1f - (dist / maxDist)) * 0.4f * pair * lineStrength * master
                drawLine(
                    color = NazoPrimary.copy(alpha = alpha),
                    start = p1,
                    end = p2,
                    strokeWidth = 2f,
                )
            }
        }
    }

    // Draw stars
    for (i in stars.indices) {
        val e = entrance[i]
        if (e <= 0f) continue
        val s = stars[i]
        val pos = starPositions[i]
        val scale = 0.3f + 0.7f * e
        val pulse = (sin(t * s.pulseFreq * 35f + s.pulsePhase) * 0.4f + 0.6f).coerceIn(0.2f, 1f)
        drawCircle(
            color = NazoPrimary.copy(alpha = 0.35f * pulse * e * master),
            radius = s.radius * 2.5f * scale,
            center = pos,
        )
        drawCircle(
            color = NazoPrimary.copy(alpha = 0.9f * pulse * e * master),
            radius = s.radius * 1.2f * scale,
            center = pos,
        )
    }
}

private fun DrawScope.drawDigitalRain(
    drops: List<RainDrop>,
    t: Float,
    appear: Float,
    master: Float,
) {
    // --- DIGITAL RAIN STYLE ---
    // Bloom: streaks start above the top edge and fall INTO the screen one
    // after another, sweeping down to their natural positions — the rain
    // "starts pouring" instead of materialising mid-air.
    val w = size.width
    val h = size.height
    for (i in drops.indices) {
        val drop = drops[i]
        val entry = easeOutCubic(stagger(appear, i, drops.size, 0.6f))
        if (entry <= 0f) continue
        val x = (drop.fracX * w)
        // Falling downwards correctly: t increases, y moves downwards (addition)
        val yBase = ((t * drop.speed * h * 1.8f + (i * 91f)) % (h + drop.length)) - drop.length
        // Entrance offset: at entry == 0 the whole streak sits above the
        // screen; at entry == 1 it is exactly at its steady-state position.
        val y = yBase - (1f - entry) * (h + drop.length * 2f)
        val alpha = drop.alpha * master

        // Glowing vertical trail line
        drawLine(
            color = NazoPrimary.copy(alpha = alpha * 0.4f),
            start = Offset(x, y),
            end = Offset(x, y + drop.length),
            strokeWidth = 2.5f,
        )
        // Bright lead droplet head at the bottom of the streak
        drawCircle(
            color = NazoSuccess.copy(alpha = alpha * 0.95f),
            radius = 3.5f,
            center = Offset(x, y + drop.length),
        )
    }
}

private fun DrawScope.drawGlowingOrbsStyle(
    orbs: List<GlowingOrb>,
    t: Float,
    appear: Float,
    master: Float,
) {
    // --- GLOWING ORBS STYLE ---
    // Bloom: each orb pops in as a small ball (quick), then springs up to
    // full size with a slight overshoot — staggered so they arrive in turn.
    val w = size.width
    val h = size.height
    for (i in orbs.indices) {
        val orb = orbs[i]
        val p = stagger(appear, i, orbs.size, 0.45f)
        if (p <= 0f) continue
        val popP = (p / 0.22f).coerceAtMost(1f)
        val growP = ((p - 0.22f) / 0.78f).coerceIn(0f, 1f)
        val scale = 0.28f * easeOutCubic(popP) + 0.72f * easeOutBack(growP)
        val fadeIn = easeOutCubic(popP)

        val driftX = sin(t * orb.driftFreq * PI.toFloat() * 2f + orb.phase) * orb.amp
        val driftY = cos(t * orb.driftFreq * PI.toFloat() * 2f + orb.phase * 1.3f) * orb.amp
        val cx = (orb.baseX + driftX).coerceIn(0.1f, 0.9f) * w
        val cy = (orb.baseY + driftY).coerceIn(0.1f, 0.9f) * h
        drawCircle(
            color = orb.color.copy(alpha = 0.12f * fadeIn * master),
            radius = orb.radius * 2f * scale,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = orb.color.copy(alpha = 0.25f * fadeIn * master),
            radius = orb.radius * scale,
            center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.drawFloatingShapes(
    shapeParticles: List<ShapeParticle>,
    cache: ParticleDrawCache,
    t: Float,
    appear: Float,
    master: Float,
) {
    // --- CLASSIC FLOATING SHAPES (default "shapes") ---
    // Bloom: each shape scales up from nothing with a springy overshoot and
    // a small entrance twist, one after another.
    val w = size.width
    val h = size.height
    val minDimension = if (w < h) w else h
    val canvas = drawContext.canvas
    cache.ensure(minDimension, shapeParticles[0].size)

    for (i in shapeParticles.indices) {
        val p = shapeParticles[i]
        val entry = stagger(appear, i, shapeParticles.size, 0.5f)
        if (entry <= 0f) continue
        val scale = easeOutBack(entry)
        val twist = (1f - easeOutCubic(entry)) * 120f
        val fadeIn = (entry * 2f).coerceAtMost(1f)

        val driftX = sin(t * p.driftFreqX * PI.toFloat() * 2f + p.phaseX) * p.ampX
        val driftY = cos(t * p.driftFreqY * PI.toFloat() * 2f + p.phaseY) * p.ampY

        val cx = (p.baseX + driftX).coerceIn(0.05f, 0.95f) * w
        val cy = (p.baseY + driftY).coerceIn(0.05f, 0.95f) * h

        val color = when (p.shape) {
            ParticleShape.CIRCLE -> NazoPrimary
            ParticleShape.SQUARE -> NazoSuccess
            ParticleShape.TRIANGLE -> NazoError
            ParticleShape.PENTAGON -> Color(0xFF4FA4FF)
            ParticleShape.STAR -> Color(0xFFFFA726)
            ParticleShape.HEXAGON -> Color(0xFF9C6ADE)
            ParticleShape.DIAMOND -> Color(0xFF26A69A)
        }.copy(alpha = p.baseAlpha * fadeIn * master)

        if (p.shape == ParticleShape.CIRCLE) {
            drawCircle(
                color = color,
                radius = cache.circleRadius * scale,
                center = Offset(cx, cy),
                style = cache.stroke,
            )
        } else {
            val path = cache.paths[p.shape.ordinal] ?: continue
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(t * p.rotSpeed * 360f + twist)
            if (scale != 1f) canvas.scale(scale, scale)
            drawPath(path = path, color = color, style = cache.stroke)
            canvas.restore()
        }
    }
}

private fun buildShapeParticles(): List<ShapeParticle> {
    val random = Random(2027)
    val shapes = ParticleShape.values()
    return List(7) { i ->
        val col = i % 3
        val row = i / 3
        val baseX = (col + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        val baseY = (row + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        ShapeParticle(
            baseX = baseX,
            baseY = baseY,
            size = 0.10f,
            shape = shapes[i],
            driftFreqX = 1f + random.nextFloat() * 2f,
            driftFreqY = 1f + random.nextFloat() * 2f,
            phaseX = random.nextFloat() * (2f * PI.toFloat()),
            phaseY = random.nextFloat() * (2f * PI.toFloat()),
            ampX = 0.08f + random.nextFloat() * 0.07f,
            ampY = 0.08f + random.nextFloat() * 0.07f,
            rotSpeed = (random.nextFloat() * 2.0f + 0.5f) * (if (random.nextBoolean()) 1f else -1f),
            baseAlpha = 0.08f + random.nextFloat() * 0.10f
        )
    }
}

private fun buildConstellationStars(): List<ConstellationStar> {
    val random = Random(3033)
    return List(32) {
        ConstellationStar(
            fracX = random.nextFloat(),
            fracY = random.nextFloat(),
            vx = (random.nextFloat() - 0.5f) * 0.4f,
            vy = (random.nextFloat() - 0.5f) * 0.4f,
            radius = 3f + random.nextFloat() * 3.5f,
            pulseFreq = 1.2f + random.nextFloat() * 2f,
            pulsePhase = random.nextFloat() * (2f * PI.toFloat()),
        )
    }
}

private fun buildRainDrops(): List<RainDrop> {
    val random = Random(4044)
    return List(22) {
        RainDrop(
            fracX = random.nextFloat(),
            speed = 0.7f + random.nextFloat() * 0.8f,
            length = 65f + random.nextFloat() * 85f,
            alpha = 0.35f + random.nextFloat() * 0.4f,
        )
    }
}

private fun buildGlowingOrbs(): List<GlowingOrb> {
    val random = Random(5055)
    val colors = listOf(NazoPrimary, NazoSuccess, Color(0xFF9C6ADE), Color(0xFFFFA726))
    return List(4) { i ->
        GlowingOrb(
            baseX = 0.2f + (i * 0.2f),
            baseY = 0.25f + ((i % 2) * 0.5f),
            radius = 70f + random.nextFloat() * 40f,
            driftFreq = 0.6f + random.nextFloat() * 0.6f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            amp = 0.12f + random.nextFloat() * 0.08f,
            color = colors[i % colors.size],
        )
    }
}
