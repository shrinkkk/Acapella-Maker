package com.shrinkkk.acapellamaker.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.MirrorMode
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Records one video take (with mic audio) to a file.
 *
 * Note: for now audio comes from CameraX. Later milestones move audio capture to the
 * Oboe engine so it can share a sample-accurate clock with the metronome and backing tracks.
 */
class TakeRecorder(private val context: Context) {

    private val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.HD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
            )
        )
        .build()

    /** Bind this use case to the camera alongside the preview. */
    val videoCapture: VideoCapture<Recorder> = VideoCapture.Builder(recorder)
        // Save front-camera video mirrored, so playback matches what you saw in the preview.
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    private var active: Recording? = null

    /**
     * Records to a temporary file and only replaces [file] once recording succeeds,
     * so a failed re-record never destroys an existing take.
     */
    fun start(file: File, onFinished: (Result<File>) -> Unit) {
        if (active != null) return
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.nameWithoutExtension + ".recording.mp4")
        temp.delete()

        var pending = videoCapture.output.prepareRecording(
            context,
            FileOutputOptions.Builder(temp).build(),
        )
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        active = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                active = null
                if (event.hasError()) {
                    temp.delete()
                    onFinished(
                        Result.failure(
                            RuntimeException("Recording failed (error ${event.error})", event.cause)
                        )
                    )
                } else {
                    file.delete()
                    if (temp.renameTo(file)) {
                        onFinished(Result.success(file))
                    } else {
                        onFinished(Result.failure(RuntimeException("Could not save take")))
                    }
                }
            }
        }
    }

    fun stop() {
        active?.stop()
    }
}
