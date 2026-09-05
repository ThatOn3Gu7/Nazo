package quiz.thaton3app.nazo.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tap effects for the Home screen's Generate button.
 *
 * Two styles, chosen in Settings -> Appearance -> "Generate button spark":
 *
 *  * [SPARKLE_TWINKLE] — the stars themselves twinkle: each of the three stars
 *    scales up and back on its own offset schedule and flashes a warm shine,
 *    with a four-point glint crossing it at the peak.
 *  * [SPARKLE_METEORS] — everything the twinkle does, plus a short meteor
 *    shower streaking across the button behind the label.
 *
 * Both are pure Canvas drawing driven by one 0..1 progress value, so the caller
 * owns the animation clock and nothing here allocates per frame.
 */

const val SPARKLE_TWINKLE = "twinkle"
const val SPARKLE_METEORS = "meteors"

data class SparkleStyle(val id: String, val label: String, val blurb: String)

val SPARKLE_STYLES = listOf(
    SparkleStyle(SPARKLE_TWINKLE, "Twinkle", "The stars pulse and flash a warm shine"),
    SparkleStyle(SPARKLE_METEORS, "Meteor Shower", "A twinkle plus meteors streaking across"),
)

/** Warm highlight the stars flash to at the peak of a twinkle. */
private val ShineColor = Color(0xFFFFD75E)

/** Where the three stars sit, relative to the icon box (0..1), and how big. */
private data class StarSpec(val x: Float, val y: Float, val scale: Float, val phase: Float)

// Mirrors the AutoAwesome glyph: one large star with two smaller companions.
private val STARS = listOf(
    StarSpec(x = 0.42f, y = 0.44f, scale = 1.00f, phase = 0.00f),
    StarSpec(x = 0.78f, y = 0.20f, scale = 0.46f, phase = 0.22f),
    StarSpec(x = 0.76f, y = 0.74f, scale = 0.38f, phase = 0.44f),
)

/**
 * Draws the three twinkling stars in place of the static icon.
 *
 * [progress] is 0..1 for one tap; at 0 the stars render at their resting size
 * and colour, so this is also the idle appearance.
 */
fun DrawScope.drawTwinklingStars(
    progress: Float,
    baseColor: Color,
    boxSize: Size,
) {
    STARS.forEach { star ->
        val local = ((progress - star.phase) / (1f - star.phase)).coerceIn(0f, 1f)
        val pulse = sin(local * PI.toFloat())
        // Reduced the scale multiplier so it doesn't grow too huge
        val scale = star.scale * (1f + 0.55f * pulse) 
        val color = lerpColor(baseColor, ShineColor, pulse)

        val cx = star.x * boxSize.width
        val cy = star.y * boxSize.height
        val radius = boxSize.minDimension * 0.30f * scale

        translate(cx, cy) {
            drawPath(path = fourPointStar(radius), color = color)
            
            if (pulse > 0.15f) {
                val glint = (pulse - 0.15f) / 0.85f
                // Reduced the reach multiplier from 1.8f to 1.3f so it stays contained
                val len = radius * (1.3f + glint * 0.6f) 
                val alpha = glint * 0.95f
                
                drawLine(
                    color = ShineColor.copy(alpha = alpha),
                    start = Offset(-len, 0f),
                    end = Offset(len, 0f),
                    strokeWidth = radius * 0.25f, 
                )
                drawLine(
                    color = ShineColor.copy(alpha = alpha * 0.8f),
                    start = Offset(0f, -len * 0.8f),
                    end = Offset(0f, len * 0.8f),
                    strokeWidth = radius * 0.25f,
                )
            }
        }
    }
}

// fun DrawScope.drawTwinklingStars(
//     progress: Float,
//     baseColor: Color,
//     boxSize: Size,
// ) {
//     STARS.forEach { star ->
//         val local = ((progress - star.phase) / (1f - star.phase)).coerceIn(0f, 1f)
//         val pulse = sin(local * PI.toFloat())
//         // Increased the scale multiplier slightly for more 'pop'
//         val scale = star.scale * (1f + 0.75f * pulse) 
//         val color = lerpColor(baseColor, ShineColor, pulse)
//
//         val cx = star.x * boxSize.width
//         val cy = star.y * boxSize.height
//         val radius = boxSize.minDimension * 0.30f * scale
//
//         translate(cx, cy) {
//             drawPath(path = fourPointStar(radius), color = color)
//
//             // Start the glint earlier and make it thicker
//             if (pulse > 0.15f) {
//                 val glint = (pulse - 0.15f) / 0.85f
//                 val len = radius * (1.8f + glint * 1.2f) // Longer reach
//                 val alpha = glint * 0.95f
//
//                 // REMOVED the 45-degree rotation so it aligns with the star points
//                 drawLine(
//                     color = ShineColor.copy(alpha = alpha),
//                     start = Offset(-len, 0f),
//                     end = Offset(len, 0f),
//                     strokeWidth = radius * 0.25f, // Thicker rays
//                 )
//                 drawLine(
//                     color = ShineColor.copy(alpha = alpha * 0.8f),
//                     start = Offset(0f, -len * 0.8f),
//                     end = Offset(0f, len * 0.8f),
//                     strokeWidth = radius * 0.25f,
//                 )
//             }
//         }
//     }
// }

/** One meteor: where it starts (relative to the button), and how it flies. */
private data class Meteor(
    val startX: Float,
    val startY: Float,
    val delay: Float,
    val speed: Float,
    val length: Float,
    val thickness: Float,
)

/**
 * A fixed, hand-tuned set so the shower looks designed rather than random —
 * and so nothing has to be allocated or seeded on each tap.
 */
private val METEORS: List<Meteor> = run {
    val rng = Random(7)
    List(12) {
        Meteor(
            // Spread the starting X coordinates across the entire width of the button
            startX = 0.2f + rng.nextFloat() * 1.8f, 
            // Spread out the vertical starting points a bit more too
            startY = -0.2f + rng.nextFloat() * 1.2f,
            delay = rng.nextFloat() * 0.35f,
            speed = 1.0f + rng.nextFloat() * 0.8f,
            length = 0.25f + rng.nextFloat() * 0.35f,
            thickness = 4.0f + rng.nextFloat() * 6.0f,
        )
    }
}

// private val METEORS: List<Meteor> = run {
//     val rng = Random(7)
//     List(12) { // Bumped up to 12 for a slightly denser shower
//         Meteor(
//             startX = 1.02f + rng.nextFloat() * 0.55f,
//             startY = -0.15f + rng.nextFloat() * 1.05f,
//             delay = rng.nextFloat() * 0.35f, // Clustered the start times a bit more
//             speed = 1.0f + rng.nextFloat() * 0.8f,
//             length = 0.25f + rng.nextFloat() * 0.35f, // Much longer tails
//             thickness = 4.0f + rng.nextFloat() * 6.0f, // Drastically thicker streaks!
//         )
//     }
// }

/** Meteors travel down-left at this angle, in radians. */
private const val METEOR_ANGLE = 2.60f

/**
 * Draws the meteor shower across the whole button.
 *
 * Meant to be drawn *under* the label so text stays readable; each meteor is a
 * tapered streak that fades as it crosses.
 */
fun DrawScope.drawMeteorShower(
    progress: Float,
    buttonSize: Size,
    tint: Color,
) {
    if (progress <= 0f) return
    val dx = cos(METEOR_ANGLE)
    val dy = sin(METEOR_ANGLE)
    val diag = buttonSize.width

    METEORS.forEach { m ->
        val local = ((progress - m.delay) / (1f - m.delay)).coerceIn(0f, 1f) * m.speed
        if (local <= 0f || local >= 1f) return@forEach

        // Fade in quickly, fade out over the second half of the flight.
                // Fade in quickly, hold full opacity longer, then fade out
        val alpha = when {
            local < 0.1f -> local / 0.1f
            local > 0.65f -> ((1f - local) / 0.35f).coerceIn(0f, 1f)
            else -> 1f
        }
        if (alpha <= 0f) return@forEach

        val travelled = local * diag * 2.0f
        val headX = m.startX * buttonSize.width + dx * travelled
        val headY = m.startY * buttonSize.height + dy * travelled
        val tailLen = m.length * diag
        val tailX = headX - dx * tailLen
        val tailY = headY - dy * tailLen

        // The streak: warm at the head, transparent at the tail.
        drawLine(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(Color.Transparent, ShineColor.copy(alpha = alpha)),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY),
            ),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = m.thickness,
        )
        // A bright head so each meteor reads as a point of light.
        drawCircle(
            color = tint.copy(alpha = alpha),
            radius = m.thickness * 0.85f,
            center = Offset(headX, headY),
        )
    }
}

/**
 * A four-point star (the classic "sparkle"): four convex points joined by
 * concave curves pulled toward the centre.
 */
private fun fourPointStar(radius: Float): Path {
    // Control points sit near the centre, which pinches the waist between
    // points and gives the concave, glinting sparkle silhouette.
    val ctrl = radius * 0.14f
    return Path().apply {
        moveTo(0f, -radius)                       // top point
        quadraticBezierTo(ctrl, -ctrl, radius, 0f)    // -> right point
        quadraticBezierTo(ctrl, ctrl, 0f, radius)     // -> bottom point
        quadraticBezierTo(-ctrl, ctrl, -radius, 0f)   // -> left point
        quadraticBezierTo(-ctrl, -ctrl, 0f, -radius)  // -> back to top
        close()
    }
}

/** Small local lerp so this file needs no extra imports. */
private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * c,
        green = from.green + (to.green - from.green) * c,
        blue = from.blue + (to.blue - from.blue) * c,
        alpha = from.alpha + (to.alpha - from.alpha) * c,
    )
}
