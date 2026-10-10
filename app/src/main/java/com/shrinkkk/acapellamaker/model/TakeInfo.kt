package com.shrinkkk.acapellamaker.model

import com.shrinkkk.acapellamaker.audio.EngineRecordingInfo
import org.json.JSONObject
import java.io.File

/**
 * Per-take sync metadata, saved next to the take's files as take_N.json.
 *
 * Alignment rule (see AudioEngine.h): sample k of take_N.wav sits at timeline frame
 * (k - roundTripFrames), where timeline frame 0 is beat 1.
 */
data class TakeInfo(
    val sampleRate: Int,
    val framesCaptured: Long,
    val estimatedRoundTripMs: Double?,
    val outputLatencyMs: Double?,
    val inputLatencyMs: Double?,
    val peak: Float,
    val bpm: Int,
    val beatsPerBar: Int,
    val metronomeOn: Boolean,
    /** Output route during the take (speaker, usb, ...). */
    val route: String,
    /** Measured round trip for that route at record time; null if it wasn't calibrated. */
    val calibratedRoundTripMs: Double?,
) {
    fun writeTo(file: File) {
        val json = JSONObject()
            .put("version", 1)
            .put("sampleRate", sampleRate)
            .put("framesCaptured", framesCaptured)
            .put("estimatedRoundTripMs", estimatedRoundTripMs ?: JSONObject.NULL)
            .put("outputLatencyMs", outputLatencyMs ?: JSONObject.NULL)
            .put("inputLatencyMs", inputLatencyMs ?: JSONObject.NULL)
            .put("peak", peak.toDouble())
            .put("bpm", bpm)
            .put("beatsPerBar", beatsPerBar)
            .put("metronomeOn", metronomeOn)
            .put("route", route)
            .put("calibratedRoundTripMs", calibratedRoundTripMs ?: JSONObject.NULL)
        file.writeText(json.toString(2))
    }

    companion object {
        /** Reads the fields needed for playback; null if the file is missing or unreadable. */
        fun read(file: File): TakeInfo? = try {
            val j = JSONObject(file.readText())
            fun optDouble(key: String): Double? = if (j.isNull(key)) null else j.optDouble(key)
            TakeInfo(
                sampleRate = j.getInt("sampleRate"),
                framesCaptured = j.optLong("framesCaptured"),
                estimatedRoundTripMs = optDouble("estimatedRoundTripMs"),
                outputLatencyMs = optDouble("outputLatencyMs"),
                inputLatencyMs = optDouble("inputLatencyMs"),
                peak = j.optDouble("peak", 0.0).toFloat(),
                bpm = j.optInt("bpm", 100),
                beatsPerBar = j.optInt("beatsPerBar", 4),
                metronomeOn = j.optBoolean("metronomeOn"),
                route = j.optString("route", "speaker"),
                calibratedRoundTripMs = optDouble("calibratedRoundTripMs"),
            )
        } catch (e: Exception) {
            null
        }

        fun from(
            info: EngineRecordingInfo,
            bpm: Int,
            beatsPerBar: Int,
            metronomeOn: Boolean,
            route: String,
            calibratedRoundTripMs: Double?,
        ) = TakeInfo(
            sampleRate = info.sampleRate,
            framesCaptured = info.framesCaptured,
            estimatedRoundTripMs = info.estimatedRoundTripMs,
            outputLatencyMs = info.outputLatencyMs,
            inputLatencyMs = info.inputLatencyMs,
            peak = info.peak,
            bpm = bpm,
            beatsPerBar = beatsPerBar,
            metronomeOn = metronomeOn,
            route = route,
            calibratedRoundTripMs = calibratedRoundTripMs,
        )
    }
}
