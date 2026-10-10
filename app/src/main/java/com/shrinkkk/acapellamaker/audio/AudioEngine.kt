package com.shrinkkk.acapellamaker.audio

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

    /** How many beats have started since the last restart (0 = none yet). */
    val beatsStarted: Long get() = nativeBeatsStarted()
    val framesRendered: Long get() = nativeFramesRendered()
    val sampleRate: Int get() = nativeSampleRate()

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
