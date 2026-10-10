package com.shrinkkk.acapellamaker.model

import java.io.File

/** One acapella: a layout plus one video file per cell. Files live in app-private storage. */
class Project(val layout: GridLayout, val dir: File) {

    fun takeFile(index: Int): File = File(dir, "take_${index + 1}.mp4")

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
