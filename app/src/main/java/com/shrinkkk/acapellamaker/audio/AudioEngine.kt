package com.shrinkkk.acapellamaker.audio

import java.io.File
import kotlin.math.log10

/**
 * Kotlin side of the native (C++/Oboe) audio engine. One engine for the whole app.
 *
 * The engine's rendered-frame counter is the master clock that metronome, backing takes and
 * recorded audio will all be placed on.
 */
object AudioEngine {
    init {
        System.loadLibrary("acapella")
    }

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()
    val isRunning: Boolean get() = nativeIsRunning()

    fun setMetronomeEnabled(enabled: Boolean) = nativeSetMetronomeEnabled(enabled)
    fun setBpm(bpm: Int) = nativeSetBpm(bpm.toDouble())
    fun setBeatsPerBar(beats: Int) = nativeSetBeatsPerBar(beats)
    fun setMetronomeVolume(volume: Float) = nativeSetMetronomeVolume(volume)

    /** Next click becomes beat 1 of a new bar. */
    fun restartBeat() = nativeRestartBeat()

    /**
     * Starts recording the mic to [wavFile] in sync with the output clock. The beat restarts at
     * the same instant, so sample 0 of the WAV is (before latency compensation) beat 1.
     * The engine must be running.
     */
    fun startRecording(wavFile: File): Boolean = nativeStartRecording(wavFile.absolutePath)

    fun stopRecording(): EngineRecordingInfo {
        val v = nativeStopRecording()
        return EngineRecordingInfo(
            framesCaptured = v[0].toLong(),
            sampleRate = v[1].toInt(),
            outputLatencyMs = v[2].takeIf { it >= 0 },
            inputLatencyMs = v[3].takeIf { it >= 0 },
            peak = v[4].toFloat(),
            overflowFrames = v[5].toLong(),
        )
    }

    /** How many beats have started since the last restart (0 = none yet). */
    val beatsStarted: Long get() = nativeBeatsStarted()
    val framesRendered: Long get() = nativeFramesRendered()
    val sampleRate: Int get() = nativeSampleRate()

    /** AudioDeviceInfo id of the output in use (0 if unknown). */
    val outputDeviceId: Int get() = nativeOutputDeviceId()

    /** AudioDeviceInfo id of the mic used by the last/current recording (0 if unknown). */
    val inputDeviceId: Int get() = nativeInputDeviceId()

    private external fun nativeStartRecording(path: String): Boolean
    private external fun nativeStopRecording(): DoubleArray
    private external fun nativeOutputDeviceId(): Int
    private external fun nativeInputDeviceId(): Int
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeIsRunning(): Boolean
    private external fun nativeSetMetronomeEnabled(enabled: Boolean)
    private external fun nativeSetBpm(bpm: Double)
    private external fun nativeSetBeatsPerBar(beats: Int)
    private external fun nativeSetMetronomeVolume(volume: Float)
    private external fun nativeRestartBeat()
    private external fun nativeBeatsStarted(): Long
    private external fun nativeFramesRendered(): Long
    private external fun nativeSampleRate(): Int
}

/** Result of one engine recording. Latencies are Oboe's estimates (null if unavailable). */
data class EngineRecordingInfo(
    val framesCaptured: Long,
    val sampleRate: Int,
    val outputLatencyMs: Double?,
    val inputLatencyMs: Double?,
    val peak: Float,
    val overflowFrames: Long,
) {
    val seconds: Double get() = if (sampleRate > 0) framesCaptured.toDouble() / sampleRate else 0.0

    /** Estimated round trip (speaker/earphones -> mic) in ms, until calibration measures it. */
    val estimatedRoundTripMs: Double?
        get() = if (outputLatencyMs != null && inputLatencyMs != null) outputLatencyMs + inputLatencyMs else null

    val peakDb: Double get() = if (peak > 0f) 20 * log10(peak.toDouble()) else -120.0
}
