package com.shrinkkk.acapellamaker.ui.record

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.shrinkkk.acapellamaker.audio.AudioEngine
import com.shrinkkk.acapellamaker.camera.TakeRecorder
import com.shrinkkk.acapellamaker.model.GridLayouts
import com.shrinkkk.acapellamaker.model.Project
import com.shrinkkk.acapellamaker.ui.camera.CameraPreview
import com.shrinkkk.acapellamaker.ui.grid.GridCanvas
import com.shrinkkk.acapellamaker.ui.metronome.MetronomeBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The record screen is the final grid. The selected cell shows the live camera (cropped
 * exactly as it will be in the final video); recording goes into that cell.
 */
@Composable
fun GridRecordScreen(
    lifecycleOwner: LifecycleOwner,
    project: Project,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { TakeRecorder(context) }
    val takeCount = project.layout.takeCount

    var selected by rememberSaveable { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    // Bumped when a cell's take changes, so its thumbnail reloads.
    val takeVersions = remember { mutableStateListOf<Int>().apply { repeat(takeCount) { add(0) } } }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = SystemClock.elapsedRealtime()
            while (true) {
                elapsedMs = SystemClock.elapsedRealtime() - start
                delay(100)
            }
        }
    }

    // --- Metronome ---
    var metronomeOn by rememberSaveable { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableIntStateOf(100) }
    var beatsPerBar by rememberSaveable { mutableIntStateOf(4) }
    var currentBeat by remember { mutableIntStateOf(-1) }

    LaunchedEffect(bpm) { AudioEngine.setBpm(bpm) }
    LaunchedEffect(beatsPerBar) { AudioEngine.setBeatsPerBar(beatsPerBar) }
    LaunchedEffect(metronomeOn) {
        if (metronomeOn) {
            AudioEngine.setBpm(bpm)
            AudioEngine.setBeatsPerBar(beatsPerBar)
            AudioEngine.setMetronomeEnabled(true)
            if (!AudioEngine.start()) {
                error = "Couldn't start audio"
                metronomeOn = false
                return@LaunchedEffect
            }
            AudioEngine.restartBeat()
            // Update the beat dots once per screen frame.
            while (true) {
                withFrameNanos { }
                val started = AudioEngine.beatsStarted
                currentBeat = if (started <= 0) -1 else ((started - 1) % beatsPerBar).toInt()
            }
        } else {
            AudioEngine.setMetronomeEnabled(false)
            AudioEngine.stop()
            currentBeat = -1
        }
    }

    // Silence the click when the app goes to the background, and when leaving this screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) metronomeOn = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.stop()
            AudioEngine.setMetronomeEnabled(false)
            AudioEngine.stop()
        }
    }

    BackHandler(enabled = !isRecording) {
        if (project.hasAnyTake()) confirmExit = true else onExit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusLine(
            text = when {
                error != null -> error!!
                isRecording -> "● Recording take ${selected + 1}   ${formatElapsed(elapsedMs)}"
                project.hasTake(selected) && takeVersions[selected] >= 0 ->
                    "Take ${selected + 1} of $takeCount - recording again replaces it"
                else -> "Take ${selected + 1} of $takeCount - tap a box to choose"
            },
            highlight = isRecording,
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            GridCanvas(
                layout = project.layout,
                modifier = Modifier.aspectRatio(GridLayouts.CANVAS_ASPECT, matchHeightConstraintsFirst = true),
            ) { index ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(enabled = !isRecording && !isSelected) {
                            selected = index
                            error = null
                        },
                ) {
                    when {
                        isSelected -> CameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            extraUseCase = recorder.videoCapture,
                        )
                        project.hasTake(index) -> TakeThumbnail(
                            file = project.takeFile(index),
                            version = takeVersions[index],
                        )
                    }
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // Outline: grey on every cell so empty black cells are visible, bright on the selected one.
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = when {
                                isSelected && isRecording -> Color.Red
                                isSelected -> Color.White
                                else -> Color.DarkGray
                            },
                            shape = RoundedCornerShape(4.dp),
                        )
                )
            }
        }

        MetronomeBar(
            enabled = !isRecording,
            on = metronomeOn,
            bpm = bpm,
            beatsPerBar = beatsPerBar,
            currentBeat = currentBeat,
            onToggle = { metronomeOn = !metronomeOn },
            onBpmChange = { bpm = it },
            onBeatsPerBarChange = { beatsPerBar = it },
            modifier = Modifier.padding(top = 12.dp),
        )

        RecordButton(
            isRecording = isRecording,
            modifier = Modifier.padding(vertical = 16.dp),
            onClick = {
                if (isRecording) {
                    recorder.stop()
                } else {
                    error = null
                    isRecording = true
                    // Start the take on beat 1. (Not sample-accurate yet: the camera's own audio
                    // start time isn't tied to the engine clock until audio moves into the engine.)
                    if (metronomeOn) AudioEngine.restartBeat()
                    val cell = selected
                    recorder.start(project.takeFile(cell)) { result ->
                        isRecording = false
                        result
                            .onSuccess {
                                takeVersions[cell] = takeVersions[cell] + 1
                                // Move on to the next empty cell, if there is one.
                                val nextEmpty = (0 until takeCount).firstOrNull { !project.hasTake(it) }
                                if (nextEmpty != null) selected = nextEmpty
                            }
                            .onFailure { error = it.message ?: "Recording failed" }
                    }
                }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this acapella?") },
            text = { Text("Starting a new one will delete the takes you've recorded here.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onExit() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun StatusLine(text: String, highlight: Boolean) {
    Text(
        text,
        color = if (highlight) Color.Red else Color.White,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).height(24.dp),
    )
}

/** A still frame from a recorded take, cropped to fill the cell like the live preview. */
@Composable
private fun TakeThumbnail(file: File, version: Int) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.asImageBitmap()
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 30.dp else 60.dp)
                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                .background(Color.Red)
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
