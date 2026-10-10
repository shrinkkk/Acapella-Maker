package com.shrinkkk.acapellamaker.model

import androidx.compose.runtime.mutableStateListOf
import java.io.File

/** One acapella: a layout plus one video file per cell. Files live in app-private storage. */
class Project(val layout: GridLayout, val dir: File) {

    // Screen settings kept on the project so they survive visiting another screen (e.g. calibration).
    var bpm: Int = 100
    var beatsPerBar: Int = 4
    var selectedCell: Int = 0

    /** Per-take mute (true by default): muted takes aren't heard while recording or in preview. */
    val muted = mutableStateListOf<Boolean>().apply { repeat(layout.takeCount) { add(true) } }

    /** Per-take volume 0..1, set in preview. Applied when the take isn't muted. */
    val volume = mutableStateListOf<Float>().apply { repeat(layout.takeCount) { add(1f) } }

    fun gainFor(index: Int): Float = if (muted[index]) 0f else volume[index]

    /** Video (with the camera's own audio track). */
    fun takeFile(index: Int): File = File(dir, "take_${index + 1}.mp4")

    /** Audio recorded by the engine on the shared clock. This is the audio used for sync. */
    fun takeAudioFile(index: Int): File = File(dir, "take_${index + 1}.wav")

    /** Sync metadata for the take (see TakeInfo). */
    fun takeInfoFile(index: Int): File = File(dir, "take_${index + 1}.json")

    /** Cached video-to-engine-audio alignment (see TakeAligner). Deleted when the take is re-recorded. */
    fun takeAlignFile(index: Int): File = File(dir, "take_${index + 1}.align.json")

    fun hasTake(index: Int): Boolean = takeFile(index).exists()

    fun hasAnyTake(): Boolean = (0 until layout.takeCount).any(::hasTake)

    companion object {
        /** Starts a fresh project, discarding the previous one (only one project for now). */
        fun createFresh(root: File, layout: GridLayout): Project {
            val dir = File(root, "current_project")
            dir.deleteRecursively()
            dir.mkdirs()
            return Project(layout, dir)
        }
    }
}
