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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.shrinkkk.acapellamaker.audio.AudioEngine
import com.shrinkkk.acapellamaker.audio.EngineRecordingInfo
import com.shrinkkk.acapellamaker.calibration.AudioRoute
import com.shrinkkk.acapellamaker.calibration.CalibrationStore
import com.shrinkkk.acapellamaker.model.TakeInfo
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
    onOpenCalibration: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { TakeRecorder(context) }
    val takeCount = project.layout.takeCount

    var selected by rememberSaveable { mutableIntStateOf(project.selectedCell) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    // Engine-side result of the take being recorded, captured when Stop is pressed.
    var engineInfo by remember { mutableStateOf<EngineRecordingInfo?>(null) }
    // One-line report about the last take's engine audio (helps check the engine is capturing).
    var lastTakeReport by remember { mutableStateOf<String?>(null) }

    // --- Calibration for the current output (speaker / earphones) ---
    val calibrationStore = remember { CalibrationStore(context) }
    var route by remember { mutableStateOf(AudioRoute.current(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!isRecording) route = AudioRoute.current(context)
            delay(1000)
        }
    }
    val calibratedMs = calibrationStore.roundTripMs(route)
    // Route and delay in effect for the take being recorded.
    var takeRoute by remember { mutableStateOf(route) }
    var takeCalibratedMs by remember { mutableStateOf<Double?>(null) }
    // Fresh group each time preview starts, sized to the takes that exist right now.
    val previewGroup = remember(previewing) {
        if (previewing) GroupPlayback((0 until takeCount).count { project.hasTake(it) }) else null
    }
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
    var bpm by rememberSaveable { mutableIntStateOf(project.bpm) }
    var beatsPerBar by rememberSaveable { mutableIntStateOf(project.beatsPerBar) }
    LaunchedEffect(bpm, beatsPerBar, selected) {
        project.bpm = bpm
        project.beatsPerBar = beatsPerBar
        project.selectedCell = selected
    }
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
        when {
            previewing -> previewing = false
            project.hasAnyTake() -> confirmExit = true
            else -> onExit()
        }
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
                previewing -> "Rough preview - takes aren't synced to each other yet"
                lastTakeReport != null && !isRecording -> lastTakeReport!!
                calibratedMs == null -> "Not calibrated for ${route.label} - tap Calibrate"
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
                        .clickable(enabled = !isRecording && !previewing && !isSelected) {
                            selected = index
                            error = null
                            lastTakeReport = null
                        },
                ) {
                    when {
                        previewing -> if (project.hasTake(index)) {
                            CroppedTakeVideo(file = project.takeFile(index), group = previewGroup!!)
                        }
                        isSelected -> CameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            extraUseCase = recorder.videoCapture,
                        )
                        project.hasTake(index) -> TakeThumbnail(
                            file = project.takeFile(index),
                            version = takeVersions[index],
                        )
                    }
                    // Cell numbers are on-screen only (hidden in preview); export renders just the videos.
                    if (!previewing) Text(
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
                            width = if (isSelected && !previewing) 3.dp else 1.dp,
                            color = when {
                                previewing -> Color.DarkGray
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
            enabled = !isRecording && !previewing,
            on = metronomeOn,
            bpm = bpm,
            beatsPerBar = beatsPerBar,
            currentBeat = currentBeat,
            onToggle = { metronomeOn = !metronomeOn },
            onBpmChange = { bpm = it },
            onBeatsPerBarChange = { beatsPerBar = it },
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = {
                        if (!previewing) metronomeOn = false
                        previewing = !previewing
                    },
                    enabled = !isRecording && (previewing || project.hasAnyTake()) && takeVersions.sum() >= 0,
                ) {
                    Text(if (previewing) "Stop" else "Preview", color = Color.White)
                }
            }
        RecordButton(
            isRecording = isRecording,
            enabled = !previewing,
            onClick = onClick@{
                if (isRecording) {
                    // Stop the engine side first so its audio ends with the button press.
                    engineInfo = AudioEngine.stopRecording()
                    recorder.stop()
                } else {
                    error = null
                    lastTakeReport = null
                    engineInfo = null
                    val cell = selected
                    takeRoute = route
                    takeCalibratedMs = calibratedMs
                    val wavTemp = File(project.dir, "take_${cell + 1}.recording.wav")

                    // The engine provides the shared clock, so it runs while recording even with
                    // the click off (it then just plays silence).
                    if (!AudioEngine.isRunning && !AudioEngine.start()) {
                        error = "Couldn't start the audio engine"
                        return@onClick
                    }
                    // Starts the mic capture and restarts the beat in the same audio callback.
                    if (!AudioEngine.startRecording(wavTemp)) {
                        error = "Couldn't open the mic in the audio engine"
                        if (!metronomeOn) AudioEngine.stop()
                        return@onClick
                    }

                    isRecording = true
                    recorder.start(project.takeFile(cell)) { result ->
                        isRecording = false
                        // Normally already stopped by the button; this covers camera-side failures.
                        val info = engineInfo ?: AudioEngine.stopRecording()
                        if (!metronomeOn) AudioEngine.stop()

                        result
                            .onSuccess {
                                project.takeAudioFile(cell).delete()
                                wavTemp.renameTo(project.takeAudioFile(cell))
                                TakeInfo.from(
                                    info, bpm, beatsPerBar, metronomeOn,
                                    route = takeRoute.key,
                                    calibratedRoundTripMs = takeCalibratedMs,
                                ).writeTo(project.takeInfoFile(cell))
                                lastTakeReport = describe(cell, info, takeRoute, takeCalibratedMs)

                                takeVersions[cell] = takeVersions[cell] + 1
                                // Move on to the next empty cell, if there is one.
                                val nextEmpty = (0 until takeCount).firstOrNull { !project.hasTake(it) }
                                if (nextEmpty != null) selected = nextEmpty
                            }
                            .onFailure {
                                wavTemp.delete()
                                error = it.message ?: "Recording failed"
                            }
                    }
                }
            },
        )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = onOpenCalibration, enabled = !isRecording && !previewing) {
                    Text("Calibrate", color = Color.White)
                }
            }
        }
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
        maxLines = 2,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 40.dp),
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
private fun RecordButton(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
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

/** e.g. "Take 2 saved · engine audio 12.4 s · peak -8 dB · delay 142 ms (USB-C earphones / adapter)" */
private fun describe(cell: Int, info: EngineRecordingInfo, route: AudioRoute, calibratedMs: Double?): String {
    val latency = calibratedMs?.let { "%.0f ms (%s)".format(it, route.label) } ?: "not calibrated (${route.label})"
    val warning = when {
        info.framesCaptured == 0L -> " · NO ENGINE AUDIO"
        info.peak < 0.001f -> " · engine audio silent?"
        info.overflowFrames > 0 -> " · ${info.overflowFrames} frames dropped"
        else -> ""
    }
    return "Take ${cell + 1} saved · engine audio %.1f s · peak %.0f dB · delay %s%s"
        .format(info.seconds, info.peakDb, latency, warning)
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
