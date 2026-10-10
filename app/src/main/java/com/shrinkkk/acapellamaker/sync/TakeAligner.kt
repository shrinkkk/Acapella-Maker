package com.shrinkkk.acapellamaker.sync

import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where a take's video sits relative to its engine audio.
 * [videoZeroInWavMs]: position in take_N.wav (ms) that the video's position 0 corresponds to.
 * [score]: how well the camera audio matched the engine audio (0..1); below ~0.3 is unreliable.
 */
data class TakeAlignment(val videoZeroInWavMs: Double, val score: Double) {
    val reliable: Boolean get() = score >= MIN_SCORE

    fun writeTo(file: File) {
        file.writeText(JSONObject().put("videoZeroInWavMs", videoZeroInWavMs).put("score", score).toString(2))
    }

    companion object {
        const val MIN_SCORE = 0.3

        fun read(file: File): TakeAlignment? = try {
            val j = JSONObject(file.readText())
            TakeAlignment(j.getDouble("videoZeroInWavMs"), j.getDouble("score"))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Finds where the video starts on the engine's timeline by matching the camera's own audio track
 * against the engine's recording of the same mic. Works on 1 ms loudness envelopes, which is far
 * finer than a video frame (~33 ms).
 */
object TakeAligner {
    private const val ENV_RATE = 1000           // envelope samples per second (1 ms)
    private const val MAX_LAG_MS = 2000         // camera may start up to 2 s before/after the engine
    private const val COMPARE_MS = 15_000       // compare this much audio

    fun align(videoFile: File, engineAudio: FloatArray, engineSampleRate: Int): TakeAlignment? {
        val camera = decodeVideoAudio(videoFile) ?: return null
        val a = envelope(camera.samples, camera.sampleRate)   // camera
        val b = envelope(engineAudio, engineSampleRate)      // engine
        if (a.size < 500 || b.size < 500) return null

        val n = min(a.size, COMPARE_MS)
        var bestLag = 0
        var bestScore = -1.0
        // a[t] lines up with b[t + lag]
        for (lag in -MAX_LAG_MS..MAX_LAG_MS) {
            val tStart = max(0, -lag)
            val tEnd = min(n, b.size - lag)
            if (tEnd - tStart < 500) continue
            var ab = 0.0
            var aa = 0.0
            var bb = 0.0
            for (t in tStart until tEnd) {
                val x = a[t].toDouble()
                val y = b[t + lag].toDouble()
                ab += x * y
                aa += x * x
                bb += y * y
            }
            if (aa <= 0.0 || bb <= 0.0) continue
            val score = ab / sqrt(aa * bb)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestScore < 0) return null

        // Camera audio sample 0 is at wav ms = bestLag. Video position 0 is firstSampleTime earlier.
        val videoZero = bestLag - camera.firstSampleTimeUs / 1000.0
        return TakeAlignment(videoZero, bestScore)
    }

    /** Loudness per 1 ms block with the average removed (so silence doesn't count as a match). */
    private fun envelope(x: FloatArray, sampleRate: Int): FloatArray {
        val block = max(1, sampleRate / ENV_RATE)
        val count = x.size / block
        val env = FloatArray(count)
        var mean = 0.0
        for (i in 0 until count) {
            var sum = 0f
            val base = i * block
            for (k in 0 until block) sum += abs(x[base + k])
            env[i] = sum / block
            mean += env[i]
        }
        mean /= max(1, count)
        for (i in 0 until count) env[i] -= mean.toFloat()
        return env
    }
}
