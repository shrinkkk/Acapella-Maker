package com.shrinkkk.acapellamaker.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shrinkkk.acapellamaker.audio.describeAudioDevice
import com.shrinkkk.acapellamaker.calibration.AudioRoute
import com.shrinkkk.acapellamaker.calibration.CalibrationStore
import com.shrinkkk.acapellamaker.calibration.LatencyCalibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Measures the speaker/earphones -> mic delay for the current output and saves it. */
@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf(AudioRoute.current(context)) }
    var running by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var savedVersion by remember { mutableIntStateOf(0) }  // bump to refresh saved values

    // Follow earphones being plugged in / unplugged.
    LaunchedEffect(Unit) {
        while (true) {
            if (!running) route = AudioRoute.current(context)
            delay(1000)
        }
    }

    BackHandler(enabled = !running) { onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("Sync calibration", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Measures how long sound takes to go out of the phone and back into the mic, " +
                    "so every take can be shifted back by exactly that much.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current output", style = MaterialTheme.typography.labelMedium)
                    Text(route.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("How to test", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (route.isEarphones) {
                            "1. Media volume to about 70%.\n" +
                                "2. Hold ONE earbud pressed against the phone's microphone " +
                                "(usually a small hole on the bottom edge).\n" +
                                "3. Stay quiet for 7 seconds while it clicks."
                        } else {
                            "1. Media volume to about 70%.\n" +
                                "2. Put the phone down in a quiet room.\n" +
                                "3. Stay quiet for 7 seconds while it clicks."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !running,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    val testedRoute = route
                    running = true
                    message = null
                    scope.launch {
                        val countdown = launch {
                            for (s in 7 downTo 1) { secondsLeft = s; delay(1000) }
                        }
                        val result = LatencyCalibrator.measure(context.cacheDir)
                        countdown.cancel()
                        running = false
                        result
                            .onSuccess {
                                store.save(testedRoute, it.roundTripMs)
                                savedVersion++
                                messageIsError = false
                                message = ("Measured %.0f ms for %s (heard %d/%d clicks, within %.1f ms). Saved.\n" +
                                    "Played on: %s\nRecorded from: %s").format(
                                    it.roundTripMs, testedRoute.label, it.clicksHeard, it.clicksPlayed, it.spreadMs,
                                    describeAudioDevice(context, it.outputDeviceId),
                                    describeAudioDevice(context, it.inputDeviceId),
                                )
                            }
                            .onFailure {
                                messageIsError = true
                                message = it.message ?: "Test failed"
                            }
                    }
                },
            ) {
                Text(if (running) "Listening… $secondsLeft" else "Start test")
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = if (messageIsError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Saved measurements", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AudioRoute.entries.forEach { r ->
                val ms = if (savedVersion >= 0) store.roundTripMs(r) else null
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ms?.let { "%.0f ms".format(it) } ?: "not measured", style = MaterialTheme.typography.bodyMedium)
                        if (ms != null) {
                            TextButton(enabled = !running, onClick = { store.clear(r); savedVersion++ }) { Text("Clear") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(enabled = !running, onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
