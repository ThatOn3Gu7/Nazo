package quiz.thaton3app.nazo.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Sound effects (Phase 7) — OPT-IN, off by default (Settings → Feedback).
 *
 * Mirrors the Haptics API shape: fire-and-forget calls that no-op unless the
 * user enabled sounds. There are NO audio assets: every effect is a short
 * soft-synth chime (sine + a quiet 2nd harmonic, 4ms attack, exponential
 * decay) rendered once into PCM, cached, and played through a short-lived
 * static AudioTrack on a dedicated daemon thread — zero work on the UI
 * thread, zero APK size cost, no third-party libraries.
 */
object Sounds {

    private const val PREFS = "nazo_sound"
    private const val KEY_ENABLED = "enabled"
    private const val SAMPLE_RATE = 22050
    private const val AMPLITUDE = 0.32

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Bright little up-chime (E5 → A5). */
    fun correct(context: Context) =
        play(context, "correct") { notes(659.25 to 70, 880.0 to 160) }

    /** Soft descending "wah" (Eb4 → Bb3) — also used for time-up. */
    fun wrong(context: Context) =
        play(context, "wrong") { notes(311.13 to 90, 233.08 to 190) }

    /** Game-complete arpeggio (C5 E5 G5 C6). */
    fun complete(context: Context) =
        play(context, "complete") { notes(523.25 to 85, 659.25 to 85, 783.99 to 85, 1046.5 to 240) }

    /** New-record fanfare (G5 C6 E6) — fires with the badge pop. */
    fun record(context: Context) =
        play(context, "record") { notes(783.99 to 95, 1046.5 to 95, 1318.5 to 320) }

    /**
     * Per-variant celebration cue, paired with the victory confetti
     * (Appearance → Celebrations). Queued on the same worker as [complete],
     * so it plays right AFTER the completion arpeggio instead of over it.
     */
    fun celebration(context: Context, style: String) = when (style) {
        // One big pop: short bright hit + ring-out.
        "burst" -> play(context, "celeb_burst") { notes(1046.5 to 60, 1568.0 to 200) }
        // Fountain: quick rising run.
        "festive" -> play(context, "celeb_festive") { notes(523.25 to 60, 659.25 to 60, 783.99 to 60, 1046.5 to 60, 1318.5 to 180) }
        // Shower: gentle falling twinkle.
        "rain" -> play(context, "celeb_rain") { notes(1318.5 to 80, 1046.5 to 80, 880.0 to 80, 659.25 to 200) }
        // Two cannons: low pop, then high pop.
        "cannons" -> play(context, "celeb_cannons") { notes(392.0 to 70, 783.99 to 70, 392.0 to 70, 1046.5 to 180) }
        // Staggered pops climbing like fireworks.
        "fireworks" -> play(context, "celeb_fireworks") { notes(1046.5 to 55, 1318.5 to 55, 1568.0 to 55, 2093.0 to 160) }
        else -> Unit // "none" or unknown → silence
    }

    // ------------------------------------------------------------------

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "nazo-sounds").apply { isDaemon = true }
    }
    private val cache = ConcurrentHashMap<String, ShortArray>()

    private fun play(context: Context, key: String, build: () -> ShortArray) {
        if (!isEnabled(context)) return
        executor.execute {
            runCatching {
                val pcm = cache.getOrPut(key) { build() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Static mode: wait out the clip on this worker (plays are
                // serialized anyway), then free the native track.
                Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 40)
                track.release()
            }
        }
    }

    /** Renders a note sequence: (frequencyHz to durationMs) pairs. */
    private fun notes(vararg parts: Pair<Double, Int>): ShortArray {
        val total = parts.sumOf { (it.second * SAMPLE_RATE) / 1000 }
        val out = ShortArray(total)
        var offset = 0
        parts.forEach { (freq, durMs) ->
            val n = (durMs * SAMPLE_RATE) / 1000
            val attack = min(n, (SAMPLE_RATE * 4) / 1000).coerceAtLeast(1)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-3.0 * i / n)
                val s = sin(2 * PI * freq * t) + 0.35 * sin(4 * PI * freq * t)
                out[offset + i] = (s / 1.35 * env * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
            }
            offset += n
        }
        return out
    }
}
