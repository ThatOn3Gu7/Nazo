package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoError

private enum class ParticleShape { CIRCLE, SQUARE, TRIANGLE, PENTAGON, STAR, HEXAGON, DIAMOND }

private data class Particle(
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

/**
 * A highly polished, organic ambient background.
 * Optimized to exactly 7 large, uniquely colored elements to guarantee smooth 60fps 
 * performance on all devices while keeping the UI clean and undistracted.
 */
@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    val particles = remember { buildParticles() }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
            progress.animateTo(0f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDimension = if (w < h) w else h
        val t = progress.value

        particles.forEach { p ->
            val driftX = sin(t * p.driftFreqX * PI.toFloat() * 2f + p.phaseX).toFloat() * p.ampX
            val driftY = cos(t * p.driftFreqY * PI.toFloat() * 2f + p.phaseY).toFloat() * p.ampY

            val cx = (p.baseX + driftX).coerceIn(0.05f, 0.95f) * w
            val cy = (p.baseY + driftY).coerceIn(0.05f, 0.95f) * h
            val r = p.size * minDimension

            val rot = t * p.rotSpeed * PI.toFloat() * 2f
            val cosA = cos(rot).toFloat()
            val sinA = sin(rot).toFloat()

            fun rx(px: Float, py: Float) = cx + px * cosA - py * sinA
            fun ry(px: Float, py: Float) = cy + px * sinA + py * cosA

            val cornerEffect = PathEffect.cornerPathEffect(r * 0.35f)
            val strokeWidth = (r * 0.08f).coerceAtLeast(3f)
            val strokeStyle = Stroke(width = strokeWidth, pathEffect = cornerEffect)

            // CIRCLE/SQUARE/TRIANGLE use brand tokens. The four accent hues below are
            // intentional decorative colors kept outside the theme palette so the ambient
            // background stays lively (no single Nazo* token maps to these hues).
            val shapeColor = when (p.shape) {
                ParticleShape.CIRCLE -> NazoPrimary
                ParticleShape.SQUARE -> NazoSuccess
                ParticleShape.TRIANGLE -> NazoError
                ParticleShape.PENTAGON -> Color(0xFF4FA4FF) // Soft Cyan / Electric Blue
                ParticleShape.STAR -> Color(0xFFFFA726)     // Warm Amber / Gold
                ParticleShape.HEXAGON -> Color(0xFF9C6ADE)  // Muted Purple / Lavender
                ParticleShape.DIAMOND -> Color(0xFF26A69A)  // Soft Teal
            }
            
            val color = shapeColor.copy(alpha = p.baseAlpha)

            // We apply different radius multipliers below so that different mathematical shapes 
            // end up sharing roughly the same visual weight (e.g. squaring a circle feels larger, so we shrink squares).
            when (p.shape) {
                ParticleShape.CIRCLE -> {
                    val cr = r * 0.9f 
                    drawCircle(
                        color = color,
                        radius = cr,
                        center = Offset(cx, cy),
                        style = strokeStyle
                    )
                }
                ParticleShape.SQUARE -> {
                    val sr = r * 0.8f // Scaled down because squares carry more visual weight
                    val path = Path().apply {
                        moveTo(rx(-sr, -sr), ry(-sr, -sr))
                        lineTo(rx(sr, -sr), ry(sr, -sr))
                        lineTo(rx(sr, sr), ry(sr, sr))
                        lineTo(rx(-sr, sr), ry(-sr, sr))
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.TRIANGLE -> {
                    val tr = r * 1.15f // Scaled up because triangles have a lot of empty space
                    val hDist = tr * 0.866f 
                    val vDist = tr * 0.5f
                    val path = Path().apply {
                        moveTo(rx(0f, -tr), ry(0f, -tr))       
                        lineTo(rx(hDist, vDist), ry(hDist, vDist))  
                        lineTo(rx(-hDist, vDist), ry(-hDist, vDist)) 
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.PENTAGON -> {
                    val pr = r * 1.05f 
                    val path = Path().apply {
                        for (i in 0 until 5) {
                            val angle = (i * 2 * PI / 5).toFloat() - (PI / 2).toFloat()
                            val px = pr * cos(angle)
                            val py = pr * sin(angle)
                            if (i == 0) moveTo(rx(px, py), ry(px, py))
                            else lineTo(rx(px, py), ry(px, py))
                        }
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.STAR -> {
                    val str = r * 1.3f // Scaled up to counteract the deep inner star troughs
                    val path = Path().apply {
                        val points = 5
                        for (i in 0 until (points * 2)) {
                            val angle = (i * PI / points).toFloat() - (PI / 2).toFloat()
                            val currentR = if (i % 2 == 0) str else str * 0.45f
                            val px = currentR * cos(angle)
                            val py = currentR * sin(angle)
                            if (i == 0) moveTo(rx(px, py), ry(px, py))
                            else lineTo(rx(px, py), ry(px, py))
                        }
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.HEXAGON -> {
                    val hr = r * 1.0f
                    val path = Path().apply {
                        for (i in 0 until 6) {
                            val angle = (i * 2 * PI / 6).toFloat() - (PI / 2).toFloat()
                            val px = hr * cos(angle)
                            val py = hr * sin(angle)
                            if (i == 0) moveTo(rx(px, py), ry(px, py))
                            else lineTo(rx(px, py), ry(px, py))
                        }
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.DIAMOND -> {
                    val dr = r * 1.15f
                    val path = Path().apply {
                        moveTo(rx(0f, -dr), ry(0f, -dr))
                        lineTo(rx(dr, 0f), ry(dr, 0f))
                        lineTo(rx(0f, dr), ry(0f, dr))
                        lineTo(rx(-dr, 0f), ry(-dr, 0f))
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
            }
        }
    }
}

private fun buildParticles(): List<Particle> {
    val random = Random(2027) 
    val shapes = ParticleShape.values()
    
    // Exactly 7 items (one of each shape) ensures perfect smooth performance on potato phones 
    // and keeps the background from looking cluttered.
    return List(7) { i ->
        // Distribute them in a 3x3 grid pattern so they start evenly spread across the screen
        val col = i % 3
        val row = i / 3
        
        val baseX = (col + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        val baseY = (row + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)

        Particle(
            baseX = baseX,
            baseY = baseY,
            size = 0.10f, // Enlarged size
            shape = shapes[i], // Exactly 1 of each of the 7 shapes
            driftFreqX = 1f + random.nextFloat() * 2f, 
            driftFreqY = 1f + random.nextFloat() * 2f,
            phaseX = random.nextFloat() * (2f * PI.toFloat()),
            phaseY = random.nextFloat() * (2f * PI.toFloat()),
            ampX = 0.08f + random.nextFloat() * 0.07f,
            ampY = 0.08f + random.nextFloat() * 0.07f,
            rotSpeed = (random.nextFloat() * 2.0f + 0.5f) * (if (random.nextBoolean()) 1f else -1f),
            baseAlpha = 0.08f + random.nextFloat() * 0.10f // Kept translucent to remain a background element
        )
    }
}

