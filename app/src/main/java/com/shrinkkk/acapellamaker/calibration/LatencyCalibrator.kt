package com.shrinkkk.acapellamaker.calibration

import com.shrinkkk.acapellamaker.audio.AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    suspend fun measure(workDir: File): Result<CalibrationResult> {
        val wav = File(workDir, "calibration.wav")
        val wasRunning = AudioEngine.isRunning
        try {
            AudioEngine.setBpm(BPM)
            AudioEngine.setBeatsPerBar(4)
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
            return withContext(Dispatchers.Default) { analyse(readWavMono16(wav), info.sampleRate) }
                .map { it.copy(outputDeviceId = outId, inputDeviceId = inId) }
        } finally {
            AudioEngine.setMetronomeEnabled(false)
            AudioEngine.setMetronomeVolume(0.8f)
            if (!wasRunning) AudioEngine.stop()
            wav.delete()
        }
    }

    private fun analyse(samples: FloatArray, sampleRate: Int): Result<CalibrationResult> {
        val framesPerBeat = sampleRate * 60.0 / BPM
        val delaysMs = mutableListOf<Double>()

        for (j in 1 until CLICKS) {
            val start = (j * framesPerBeat).toInt()
            // Look up to 80% of a beat later; delays beyond ~800 ms aren't realistic.
            val end = min(samples.size, (start + 0.8 * framesPerBeat).toInt())
            if (end - start < sampleRate / 10) break

            // Background level just before the click is due.
            var noise = 0f
            for (i in max(0, start - sampleRate / 20) until start) noise = max(noise, abs(samples[i]))

            var peak = 0f
            for (i in start until end) peak = max(peak, abs(samples[i]))
            if (peak < 0.02f || peak < noise * 4f) continue  // click not clearly heard

            // Onset = first sample reaching 30% of the click's peak.
            val threshold = 0.3f * peak
            var onset = -1
            for (i in start until end) {
                if (abs(samples[i]) >= threshold) { onset = i; break }
            }
            if (onset >= 0) delaysMs += (onset - start) * 1000.0 / sampleRate
        }

        if (delaysMs.size < MIN_CLICKS_HEARD) {
            return Result.failure(
                IllegalStateException(
                    "Only heard ${delaysMs.size} of ${CLICKS - 1} clicks. " +
                        "Turn the volume up, keep it quiet, and (with earphones) press an earbud right on the mic."
                )
            )
        }
        val sorted = delaysMs.sorted()
        val median = sorted[sorted.size / 2]
        val spread = sorted.last() - sorted.first()
        if (spread > MAX_SPREAD_MS) {
            return Result.failure(
                IllegalStateException(
                    "Measurements disagreed by %.1f ms (%s). Try again somewhere quieter."
                        .format(spread, sorted.joinToString { "%.0f".format(it) })
                )
            )
        }
        return Result.success(CalibrationResult(median, spread, delaysMs.size, CLICKS - 1))
    }

    /** Reads the engine's own WAV format: 44-byte header, mono 16-bit PCM. */
    private fun readWavMono16(file: File): FloatArray {
        RandomAccessFile(file, "r").use { raf ->
            val dataBytes = (raf.length() - 44).toInt().coerceAtLeast(0)
            val bytes = ByteArray(dataBytes)
            raf.seek(44)
            raf.readFully(bytes)
            val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
        }
    }
}
