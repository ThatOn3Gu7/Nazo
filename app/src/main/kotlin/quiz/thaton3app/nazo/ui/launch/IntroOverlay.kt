package quiz.thaton3app.nazo.ui.launch

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import quiz.thaton3app.nazo.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// The exact launcher-icon / splash background greens (see res/values/colors.xml
// and values-night/colors.xml) so the system splash hands off to this overlay
// with zero visible seam. Chosen by the APP's theme mode (isDark from NazoApp),
// not the OS, so post-splash the tile matches what the app will actually show.
private val IntroBackgroundLight = Color(0xFF36A06F)
private val IntroBackgroundDark = Color(0xFF246D4C)

// Logo box. 220dp (owner asked for two ~10px bumps over the original 200dp
// splash icon box; the size step at the splash→intro handoff frame is
// imperceptible because both are centered on the same tile).
private val LOGO_SIZE = 220.dp

// Particle budget: ~700 dots reproduce the 謎 clearly and draw in well under a
// frame (one Canvas, no per-particle allocations — Offset is a value class).
private const val MAX_PARTICLES = 750

/**
 * One particle of the kanji-assembly intro. Geometry is stored resolution-
 * independently: targets as fractions of the logo box (-0.5..0.5), scatter
 * start as a direction + distance in fractions of the canvas' max dimension
 * (so particles fly in from "all over the screen" on any device).
 */
private class IntroParticle(
    val targetX: Float,
    val targetY: Float,
    val dirX: Float,
    val dirY: Float,
    val dist: Float,
    val radius: Float,   // fraction of the logo box
    val stagger: Float,  // 0..0.35 — staggered departures/arrivals
    val bow: Float,      // curved-flight strength (± = which side it bows)
    val color: Color,
)

/**
 * Samples the launcher glyph bitmap into particles: decode at 1/8 (512→64px),
 * every sufficiently-opaque cell becomes a candidate, evenly thinned to
 * MAX_PARTICLES. Deterministic (seeded Random) so every cold start plays the
 * same choreography. Returns emptyList() on ANY failure → the overlay falls
 * back to the classic scale-pop intro (never a broken animation).
 */
private fun sampleParticles(context: Context): List<IntroParticle> = try {
    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground, opts)
    if (bmp == null) emptyList() else {
        val w = bmp.width
        val h = bmp.height
        val solid = ArrayList<Int>(w * h / 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((bmp.getPixel(x, y) ushr 24) > 96) solid.add(y * w + x)
            }
        }
        val step = ((solid.size + MAX_PARTICLES - 1) / MAX_PARTICLES).coerceAtLeast(1)
        val rnd = Random(0x5A50)
        val particles = ArrayList<IntroParticle>(MAX_PARTICLES)
        var i = 0
        while (i < solid.size) {
            val cell = solid[i]
            val x = cell % w
            val y = cell / w
            val pixel = bmp.getPixel(x, y)
            val angle = rnd.nextFloat() * (2f * PI.toFloat())
            particles.add(
                IntroParticle(
                    targetX = (x + 0.5f) / w - 0.5f,
                    targetY = (y + 0.5f) / h - 0.5f,
                    dirX = cos(angle),
                    dirY = sin(angle),
                    dist = 0.55f + rnd.nextFloat() * 0.5f,
                    radius = 0.009f + rnd.nextFloat() * 0.006f,
                    stagger = rnd.nextFloat() * 0.35f,
                    bow = (rnd.nextFloat() - 0.5f) * 0.45f,
                    color = Color(pixel or 0xFF000000.toInt()),
                )
            )
            i += step
        }
        bmp.recycle()
        particles
    }
} catch (_: Throwable) {
    emptyList()
}

/**
 * Branded cold-start intro, v2: the 謎 assembles itself out of ~700 particles.
 * Plays ONCE per cold start, layered over the already-composed app (nothing
 * underneath is delayed). Timeline (~2.4s total):
 *
 *   crisp glyph hold 100ms (seamless handoff from the system splash, which
 *   shows the same assembled glyph) → glyph dissolves & BURSTS into particles
 *   flying out across/off the screen (480ms) → beat at full scatter (80ms) →
 *   particles fly home on curved, staggered paths and reassemble the kanji
 *   (950ms, FastOutSlowInEasing) → sharpen back into the crisp glyph + settle
 *   pop 0.965→1.0 (260ms) → hold 180ms → whole overlay fades out 380ms →
 *   removed from the tree.
 *
 * If particle sampling fails, falls back to the original scale-pop intro
 * (0.9→1.0), so the animation can never appear broken.
 *
 * While visible it is an input barrier: a pointerInput loop consumes every
 * pointer change so taps can't leak to the UI below (self-cancels the moment
 * the overlay leaves composition). Warm starts don't replay it — the
 * composition (and the dismissed flag) survive while the activity is alive.
 */
@Composable
fun IntroOverlay(isDark: Boolean) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val context = LocalContext.current
    // null = still sampling (glyph shows crisp, identical to the splash);
    // empty = sampling failed → classic pop fallback.
    var particles by remember { mutableStateOf<List<IntroParticle>?>(null) }
    LaunchedEffect(Unit) {
        particles = withContext(Dispatchers.Default) { sampleParticles(context) }
    }

    val assemble = remember { Animatable(1f) }     // 1 = assembled, 0 = scattered
    val imageAlpha = remember { Animatable(1f) }   // crisp glyph ↔ particles crossfade
    val logoScale = remember { Animatable(1f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(particles) {
        val parts = particles ?: return@LaunchedEffect
        if (parts.isEmpty()) {
            // Fallback: the original Phase 2 pop (0.9 → 1.0).
            logoScale.snapTo(0.9f)
            delay(250)
            logoScale.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            delay(450)
        } else {
            delay(100)
            // Dissolve the crisp glyph into the (still assembled) particles while
            // they start bursting outward — reads as the kanji shattering.
            launch { imageAlpha.animateTo(0f, tween(durationMillis = 200)) }
            assemble.animateTo(0f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
            // A short beat at full scatter so the burst is readable before the return.
            delay(80)
            // Fly home: staggered, curved paths (see the Canvas below).
            assemble.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
            // Sharpen into the real glyph with a small settle pop.
            launch { imageAlpha.animateTo(1f, tween(durationMillis = 220)) }
            logoScale.snapTo(0.965f)
            logoScale.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
            delay(180)
        }
        overlayAlpha.animateTo(0f, tween(durationMillis = 380))
        dismissed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // graphicsLayer BEFORE background so the fade applies to the whole
            // overlay (tile + particles + logo) as one layer.
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(if (isDark) IntroBackgroundDark else IntroBackgroundLight)
            .pointerInput(Unit) {
                // Input barrier while the intro is showing.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val parts = particles
        if (!parts.isNullOrEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val particleAlpha = 1f - imageAlpha.value
                if (particleAlpha > 0.01f) {
                    val boxPx = LOGO_SIZE.toPx()
                    val maxDim = max(size.width, size.height)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val a = assemble.value
                    for (p in parts) {
                        // Per-particle stagger + smoothstep so departures and
                        // arrivals are individual, not a single rigid mass.
                        val raw = ((a - p.stagger) / (1f - p.stagger)).coerceIn(0f, 1f)
                        val t = raw * raw * (3f - 2f * raw)
                        val sx = cx + p.dirX * p.dist * maxDim
                        val sy = cy + p.dirY * p.dist * maxDim
                        val tx = cx + p.targetX * boxPx
                        val ty = cy + p.targetY * boxPx
                        // Curved flight: perpendicular bow, strongest mid-flight,
                        // zero at both endpoints (so landings are exact).
                        val bowAmt = p.bow * boxPx * sin(t * PI.toFloat())
                        drawCircle(
                            color = p.color,
                            radius = p.radius * boxPx,
                            center = Offset(
                                x = sx + (tx - sx) * t - p.dirY * bowAmt,
                                y = sy + (ty - sy) * t + p.dirX * bowAmt,
                            ),
                            alpha = particleAlpha,
                        )
                    }
                }
            }
        }
        // 210dp crisp glyph — the splash icon hands off to this, and the
        // particles crossfade back into it at the end of the assembly.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(LOGO_SIZE)
                .graphicsLayer {
                    alpha = imageAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
        )
    }
}
