package com.shrinkkk.acapellamaker.sync

import android.media.MediaPlayer
import android.os.SystemClock
import com.shrinkkk.acapellamaker.audio.AudioEngine
import com.shrinkkk.acapellamaker.audio.readEngineWav
import com.shrinkkk.acapellamaker.model.Project
import com.shrinkkk.acapellamaker.model.TakeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * One take as placed on the shared timeline (ms, 0 = beat 1).
 * [videoStartMs]: timeline time at which the video's position 0 should be shown.
 * [cameraDelayMs]: how long after beat 1 the camera actually started (diagnostic).
 */
data class PlacedTake(
    val index: Int,
    val videoStartMs: Double,
    val roundTripMs: Double,
    val cameraDelayMs: Double?,
    val alignmentReliable: Boolean,
)

object PreviewLoader {
    /**
     * Aligns takes (cached per take), loads their engine audio into the engine shifted back by
     * the measured round trip, and returns where each video belongs on the timeline.
     */
    suspend fun load(
        project: Project,
        fallbackRoundTripMs: Double?,
        exclude: Int? = null,
    ): List<PlacedTake> =
        withContext(Dispatchers.Default) {
            AudioEngine.clearTracks()
            val placed = mutableListOf<PlacedTake>()
            for (i in 0 until project.layout.takeCount) {
                if (i == exclude) continue
                if (!project.hasTake(i) || !project.takeAudioFile(i).exists()) continue
                val info = TakeInfo.read(project.takeInfoFile(i))
                val sampleRate = info?.sampleRate ?: AudioEngine.sampleRate
                val roundTripMs = info?.calibratedRoundTripMs ?: fallbackRoundTripMs ?: 0.0
                val audio = readEngineWav(project.takeAudioFile(i))

                val alignFile = project.takeAlignFile(i)
                val alignment = TakeAlignment.read(alignFile)
                    ?: TakeAligner.align(project.takeFile(i), audio, sampleRate)?.also { it.writeTo(alignFile) }
                val reliable = alignment?.reliable == true

                // Recorded sample k belongs at timeline frame k - roundTrip.
                val roundTripFrames = (roundTripMs * sampleRate / 1000.0).roundToLong()
                AudioEngine.setTakeGain(i, project.gainFor(i))
                AudioEngine.addTrack(audio, startFrame = -roundTripFrames, slot = i)

                val videoZeroInWav = if (reliable) alignment!!.videoZeroInWavMs else 0.0
                placed += PlacedTake(
                    index = i,
                    videoStartMs = videoZeroInWav - roundTripMs,
                    roundTripMs = roundTripMs,
                    cameraDelayMs = if (reliable) alignment!!.videoZeroInWavMs else null,
                    alignmentReliable = reliable,
                )
            }
            placed
        }
}

/**
 * Keeps muted video players following the engine's audio. Audio is the master.
 *
 * Small drift is corrected by playing the video very slightly faster/slower (invisible), and
 * only large jumps (start, loop, big drift) use a seek, because seeking makes video stutter.
 */
class VideoSyncController {
    private class Entry(val player: MediaPlayer) {
        var lastSeekAt = 0L
        var speed = 1f
    }

    private val players = mutableMapOf<Int, Entry>()
    var placement: Map<Int, PlacedTake> = emptyMap()

    fun register(index: Int, player: MediaPlayer) {
        player.setVolume(0f, 0f)  // sound comes from the engine
        players[index] = Entry(player)
    }

    fun unregister(index: Int) {
        players.remove(index)
    }

    /** [heardMs]: timeline time currently coming out of the speaker/earphones. */
    fun sync(heardMs: Double) {
        val now = SystemClock.elapsedRealtime()
        for ((index, entry) in players) {
            val take = placement[index] ?: continue
            val player = entry.player
            try {
                val duration = player.duration.toLong()
                if (duration <= 0) continue
                val target = (heardMs - take.videoStartMs).roundToLong()

                if (target < 0 || target >= duration) {
                    // Before this take starts / after it ends: hold still.
                    if (player.isPlaying) player.pause()
                    val parkAt = target.coerceIn(0L, duration - 1)
                    if (abs(player.currentPosition - parkAt) > 200 && now - entry.lastSeekAt > 500) {
                        player.seekTo(parkAt, MediaPlayer.SEEK_CLOSEST)
                        entry.lastSeekAt = now
                    }
                    continue
                }

                if (!player.isPlaying) {
                    player.seekTo(target, MediaPlayer.SEEK_CLOSEST)
                    setSpeed(entry, 1f)
                    player.start()
                    entry.lastSeekAt = now
                    continue
                }
                if (now - entry.lastSeekAt < 400) continue  // let a seek settle before judging drift

                val drift = player.currentPosition - target  // > 0: video ahead of audio
                when {
                    abs(drift) > 250 -> {
                        player.seekTo(target, MediaPlayer.SEEK_CLOSEST)
                        entry.lastSeekAt = now
                    }
                    drift > 45 -> setSpeed(entry, 0.95f)
                    drift < -45 -> setSpeed(entry, 1.05f)
                    abs(drift) < 12 -> setSpeed(entry, 1f)
                }
            } catch (e: IllegalStateException) {
                // Player being released; ignore.
            }
        }
    }

    private fun setSpeed(entry: Entry, speed: Float) {
        if (entry.speed == speed) return
        // Setting playback params on a paused player would start it, so only while playing.
        if (!entry.player.isPlaying) return
        entry.player.playbackParams = entry.player.playbackParams.setSpeed(speed)
        entry.speed = speed
    }
}
