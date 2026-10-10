package com.shrinkkk.acapellamaker.calibration

import com.shrinkkk.acapellamaker.audio.AudioEngine
import com.shrinkkk.acapellamaker.audio.readEngineWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class CalibrationResult(
    val roundTripMs: Double,
    val spreadMs: Double,
    val clicksHeard: Int,
    val clicksPlayed: Int,
    val outputDeviceId: Int = 0,
    val inputDeviceId: Int = 0,
)

/**
 * Loopback test: the engine plays clicks once per second while recording the mic through the
 * exact same path used for takes. Click j is played at timeline frame j * framesPerBeat, so the
 * gap until it shows up in the recording is the round-trip delay we need to subtract from takes.
 */
object LatencyCalibrator {
    private const val BPM = 60
    private const val CLICKS = 7            // click 0 is ignored (mic may still be settling)
    private const val MAX_SPREAD_MS = 5.0   // measurements must agree this closely
    private const val MIN_CLICKS_HEARD = 4
    private const val MIN_MATCH = 0.35       // how closely a sound must match the click shape

    // Must match the engine's accent click (AudioEngine.cpp: 1600 Hz, decay 120/s).
    private const val CLICK_HZ = 1600.0
    private const val CLICK_DECAY = 120.0
    private const val TEMPLATE_MS = 12

    suspend fun measure(workDir: File): Result<CalibrationResult> {
        val wav = File(workDir, "calibration.wav")
        val wasRunning = AudioEngine.isRunning
        try {
            // Nothing else may play during the test.
            AudioEngine.clearTracks()
            AudioEngine.setBpm(BPM)
            AudioEngine.setBeatsPerBar(1)  // every click is the same (accent) click
            AudioEngine.setMetronomeVolume(1.0f)
            AudioEngine.setMetronomeEnabled(true)
            if (!wasRunning && !AudioEngine.start()) {
                return Result.failure(IllegalStateException("Couldn't start the audio engine"))
            }
            if (!AudioEngine.startRecording(wav)) {
                return Result.failure(IllegalStateException("Couldn't open the microphone"))
            }
            // Record all clicks plus the last click's full window.
            delay((CLICKS * 1000L) + 300L)
            val info = AudioEngine.stopRecording()
            if (info.framesCaptured == 0L) {
                return Result.failure(IllegalStateException("The microphone recorded nothing"))
            }
            val outId = AudioEngine.outputDeviceId
            val inId = AudioEngine.inputDeviceId
            return withContext(Dispatchers.Default) { analyse(readEngineWav(wav), info.sampleRate) }
                .map { it.copy(outputDeviceId = outId, inputDeviceId = inId) }
        } finally {
            AudioEngine.setMetronomeEnabled(false)
            AudioEngine.setMetronomeVolume(0.8f)
            if (!wasRunning) AudioEngine.stop()
            wav.delete()
        }
    }

    /**
     * Finds each click in the recording with a matched filter: slides the known click shape
     * along the recording and picks the best match. Much more robust than a loudness threshold
     * when the click is quiet, there's background noise, or the phone processes the mic.
     */
    private fun analyse(samples: FloatArray, sampleRate: Int): Result<CalibrationResult> {
        val framesPerBeat = sampleRate * 60.0 / BPM
        val template = FloatArray(sampleRate * TEMPLATE_MS / 1000) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * PI * CLICK_HZ * t) * exp(-t * CLICK_DECAY)).toFloat()
        }
        var templateEnergy = 0.0
        for (v in template) templateEnergy += v * v
        val templateNorm = sqrt(templateEnergy)

        val delaysMs = mutableListOf<Double>()
        var loudest = 0f
        var bestMatchSeen = 0.0

        for (j in 1 until CLICKS) {
            val start = (j * framesPerBeat).toInt()
            // Look up to 80% of a beat later; delays beyond ~800 ms aren't realistic.
            val lastLag = min(samples.size - template.size, (start + 0.8 * framesPerBeat).toInt())
            if (lastLag - start < sampleRate / 10) break

            for (i in start until lastLag) loudest = max(loudest, abs(samples[i]))

            // Running energy of the recording under the template, for a 0..1 match score.
            var windowEnergy = 0.0
            for (k in template.indices) windowEnergy += samples[start + k] * samples[start + k]

            var bestScore = 0.0
            var bestLag = -1
            for (lag in start until lastLag) {
                var dot = 0.0
                for (k in template.indices) dot += samples[lag + k] * template[k]
                val denom = templateNorm * sqrt(max(windowEnergy, 1e-12))
                val score = dot / denom
                if (score > bestScore) {
                    bestScore = score
                    bestLag = lag
                }
                // Slide the window by one sample.
                val out = samples[lag]
                val inn = samples[lag + template.size]
                windowEnergy += inn * inn - out * out
            }
            bestMatchSeen = max(bestMatchSeen, bestScore)
            if (bestLag >= 0 && bestScore >= MIN_MATCH) {
                delaysMs += (bestLag - start) * 1000.0 / sampleRate
            }
        }

        val loudestDb = if (loudest > 0f) 20 * log10(loudest.toDouble()) else -120.0
        if (delaysMs.size < MIN_CLICKS_HEARD) {
            return Result.failure(
                IllegalStateException(
                    ("Only heard %d of %d clicks (loudest sound %.0f dB, best match %.2f). " +
                        "Press the earbud right onto the mic hole and keep the room quiet.")
                        .format(delaysMs.size, CLICKS - 1, loudestDb, bestMatchSeen)
                )
            )
        }
        // Use the clicks that agree with the median (drops an odd one caused by a noise).
        val sorted = delaysMs.sorted()
        val median = sorted[sorted.size / 2]
        val agreeing = sorted.filter { abs(it - median) <= MAX_SPREAD_MS / 2 }
        if (agreeing.size < MIN_CLICKS_HEARD) {
            return Result.failure(
                IllegalStateException(
                    "Measurements disagreed (%s ms). Try again somewhere quieter."
                        .format(sorted.joinToString { "%.0f".format(it) })
                )
            )
        }
        val result = agreeing.average()
        val spread = agreeing.last() - agreeing.first()
        return Result.success(CalibrationResult(result, spread, agreeing.size, CLICKS - 1))
    }
}
