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

    /** A single faint tap — selection, next-question, tab switch, correct answer. */
    fun light(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(18, 45))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(18)
        }
    }

    /** Two quick faint taps — signals a wrong answer ("Bzzz Bzzz"). */
    fun doubleLight(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 18, 50, 18),
                    intArrayOf(0, 45, 0, 45),
                    -1,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 18, 50, 18), -1)
        }
    }

    /** One tick at a custom amplitude (1..255) — used by the timer escalation. */
    fun tick(context: Context, amplitude: Int) {
        val v = vibrator(context) ?: return
        val amp = amplitude.coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(22, amp))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(22)
        }
    }

    /** A noticeably stronger buzz — the final countdown second. */
    fun strong(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(90, 200))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(90)
        }
    }
}
