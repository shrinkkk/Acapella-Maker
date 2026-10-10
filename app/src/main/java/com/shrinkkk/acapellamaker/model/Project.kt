package com.shrinkkk.acapellamaker.model

import java.io.File

/** One acapella: a layout plus one video file per cell. Files live in app-private storage. */
class Project(val layout: GridLayout, val dir: File) {

    // Screen settings kept on the project so they survive visiting another screen (e.g. calibration).
    var bpm: Int = 100
    var beatsPerBar: Int = 4
    var selectedCell: Int = 0

    /** Video (with the camera's own audio track). */
    fun takeFile(index: Int): File = File(dir, "take_${index + 1}.mp4")

    /** Audio recorded by the engine on the shared clock. This is the audio used for sync. */
    fun takeAudioFile(index: Int): File = File(dir, "take_${index + 1}.wav")

    /** Sync metadata for the take (see TakeInfo). */
    fun takeInfoFile(index: Int): File = File(dir, "take_${index + 1}.json")

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
