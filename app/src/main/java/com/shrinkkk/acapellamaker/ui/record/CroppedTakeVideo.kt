package com.shrinkkk.acapellamaker.ui.record

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Shows [file] inside the cell, centre-cropped exactly like the live camera preview.
 * The player is handed out via [onReady] once prepared; whoever receives it controls playback.
 */
@Composable
fun CroppedTakeVideo(
    file: File,
    onReady: (MediaPlayer) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
                val textureView = this
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var player: MediaPlayer? = null
                    private var surface: Surface? = null
                    private val videoSize = displaySize(file)

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        applyCenterCrop(textureView, width, height, videoSize)
                        val s = Surface(st)
                        surface = s
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setSurface(s)
                            setOnPreparedListener { onReady(it) }
                            prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        applyCenterCrop(textureView, width, height, videoSize)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        player?.let {
                            onReleased()
                            it.release()
                        }
                        player = null
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** The video's size as displayed, i.e. with its rotation applied. */
private fun displaySize(file: File): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) h to w else w to h
    } catch (e: Exception) {
        0 to 0
    } finally {
        retriever.release()
    }
}

/** TextureView stretches video to fill; scale it back up so it's centre-cropped instead. */
private fun applyCenterCrop(view: TextureView, viewW: Int, viewH: Int, video: Pair<Int, Int>) {
    val (videoW, videoH) = video
    if (viewW == 0 || viewH == 0 || videoW == 0 || videoH == 0) return
    val viewAspect = viewW.toFloat() / viewH
    val videoAspect = videoW.toFloat() / videoH
    val matrix = Matrix()
    if (videoAspect > viewAspect) {
        matrix.setScale(videoAspect / viewAspect, 1f, viewW / 2f, viewH / 2f)
    } else {
        matrix.setScale(1f, viewAspect / videoAspect, viewW / 2f, viewH / 2f)
    }
    view.setTransform(matrix)
}
