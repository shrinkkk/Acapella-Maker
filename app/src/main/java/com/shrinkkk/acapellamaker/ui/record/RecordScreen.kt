package com.shrinkkk.acapellamaker.ui.record

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.shrinkkk.acapellamaker.camera.TakeRecorder
import com.shrinkkk.acapellamaker.ui.camera.CameraPreview
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun RecordScreen(
    lifecycleOwner: LifecycleOwner,
    takeFile: File,
    onTakeRecorded: (File) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { TakeRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = SystemClock.elapsedRealtime()
            while (true) {
                elapsedMs = SystemClock.elapsedRealtime() - start
                delay(100)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { recorder.stop() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(lifecycleOwner = lifecycleOwner, extraUseCase = recorder.videoCapture)

        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isRecording) {
                Text(
                    formatElapsed(elapsedMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }
        }

        RecordButton(
            isRecording = isRecording,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            onClick = {
                if (isRecording) {
                    recorder.stop()
                } else {
                    error = null
                    isRecording = true
                    recorder.start(takeFile) { result ->
                        isRecording = false
                        result
                            .onSuccess(onTakeRecorded)
                            .onFailure { error = it.message ?: "Recording failed" }
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 32.dp else 64.dp)
                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                .background(Color.Red)
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
