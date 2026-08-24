package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Amplitude-based haptics. We use the platform [Vibrator] (not Compose's
 * LocalHapticFeedback) because the quiz timer needs escalating strengths that
 * the generic haptic types can't express. Safe to call from any thread; no-ops
 * silently when vibration isn't available.
 *
 * Amplitudes are 1..255 (255 = max the device can produce). The timer scales by
 * *percentage* of that max so the escalation reads as a clear ramp.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun oneShot(context: Context, ms: Long, amplitude: Int) {
        val v = vibrator(context) ?: return
        val amp = amplitude.coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    private fun pct(percent: Int): Int =
        (percent.coerceIn(0, 100) * 255 / 100).toInt().coerceIn(1, 255)

    /** Baseline selection tap — clearly felt (was far too weak before). */
    fun light(context: Context) = oneShot(context, 30, 220)

    /** Two quick taps — signals a wrong answer ("Bzzz Bzzz"). */
    fun doubleLight(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 55, 30),
                    intArrayOf(0, 220, 0, 220),
                    -1,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, 55, 30), -1)
        }
    }

    /** One tick at a percentage (0..100) of max amplitude — used by the timer ramp. */
    fun tick(context: Context, percent: Int) = oneShot(context, 35, pct(percent))

    /** Strong buzz when time runs out — 100% strength for ~130ms. */
    fun timeUp(context: Context) = oneShot(context, 130, 255)
}
