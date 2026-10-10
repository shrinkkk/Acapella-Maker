package com.shrinkkk.acapellamaker.ui.metronome

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val MIN_BPM = 40
const val MAX_BPM = 240

/** Time signatures offered. The metronome clicks [beats] times per bar at the set BPM. */
data class TimeSignature(val beats: Int, val label: String)

val TIME_SIGNATURES = listOf(
    TimeSignature(2, "2/4"),
    TimeSignature(3, "3/4"),
    TimeSignature(4, "4/4"),
    TimeSignature(5, "5/4"),
    TimeSignature(6, "6/8"),
    TimeSignature(7, "7/8"),
)

fun timeSignatureLabel(beats: Int): String =
    TIME_SIGNATURES.firstOrNull { it.beats == beats }?.label ?: "$beats/4"

/**
 * Metronome controls: on/off, tempo (-/+ and tap tempo), beats per bar, and a beat indicator.
 * [currentBeat] is 0-based within the bar, or -1 when nothing is playing.
 */
@Composable
fun MetronomeBar(
    enabled: Boolean,
    on: Boolean,
    bpm: Int,
    beatsPerBar: Int,
    currentBeat: Int,
    onToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onBeatsPerBarChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapTimes = remember { ArrayDeque<Long>() }
    var showBpmSlider by remember { mutableStateOf(false) }
    var showTimeSigMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill(
                text = if (on) "♪ On" else "♪ Off",
                active = on,
                onClick = { if (enabled) onToggle() },
            )
            Pill(text = "−", onClick = { if (enabled) onBpmChange((bpm - 1).coerceAtLeast(MIN_BPM)) })
            // Tap the tempo to open a slider.
            Text(
                "$bpm BPM",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                // Fixed width + centred text: equal gaps to - and +, and no jumping between 99 and 100.
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(68.dp)
                    .clickable { if (enabled) showBpmSlider = true }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Pill(text = "+", onClick = { if (enabled) onBpmChange((bpm + 1).coerceAtMost(MAX_BPM)) })
            Pill(text = "Tap", onClick = {
                if (!enabled) return@Pill
                val now = SystemClock.elapsedRealtime()
                // Forget old taps after a pause of 2 s.
                if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) tapTimes.clear()
                tapTimes.addLast(now)
                while (tapTimes.size > 5) tapTimes.removeFirst()
                if (tapTimes.size >= 2) {
                    val avgMs = (tapTimes.last() - tapTimes.first()).toDouble() / (tapTimes.size - 1)
                    onBpmChange((60_000.0 / avgMs).roundToInt().coerceIn(MIN_BPM, MAX_BPM))
                }
            })
            Box {
                Pill(text = timeSignatureLabel(beatsPerBar) + " ▾", onClick = { if (enabled) showTimeSigMenu = true })
                DropdownMenu(expanded = showTimeSigMenu, onDismissRequest = { showTimeSigMenu = false }) {
                    TIME_SIGNATURES.forEach { sig ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    sig.label,
                                    fontWeight = if (sig.beats == beatsPerBar) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onBeatsPerBarChange(sig.beats)
                                showTimeSigMenu = false
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp).height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(beatsPerBar) { i ->
                val lit = on && i == currentBeat
                Box(
                    Modifier
                        .size(if (i == 0) 12.dp else 9.dp)
                        .background(
                            when {
                                lit && i == 0 -> Color(0xFFFFC107)
                                lit -> Color.White
                                else -> Color.DarkGray
                            },
                            CircleShape,
                        )
                )
            }
        }
    }

    if (showBpmSlider) {
        AlertDialog(
            onDismissRequest = { showBpmSlider = false },
            title = { Text("Tempo: $bpm BPM") },
            text = {
                Column {
                    // Changes apply live, so you can hear the new tempo while sliding.
                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { onBpmChange(it.roundToInt().coerceIn(MIN_BPM, MAX_BPM)) },
                        valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("$MIN_BPM", style = MaterialTheme.typography.labelSmall)
                        Text("$MAX_BPM", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBpmSlider = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun Pill(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(if (active) Color(0xFF2E7D32) else Color(0xFF2A2A2A), RoundedCornerShape(17.dp))
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}
