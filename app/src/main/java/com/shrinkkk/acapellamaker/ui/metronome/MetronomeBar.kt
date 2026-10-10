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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val MIN_BPM = 40
const val MAX_BPM = 240
val BEATS_PER_BAR_OPTIONS = listOf(2, 3, 4, 6)

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = if (on) "♪ On" else "♪ Off",
                active = on,
                onClick = { if (enabled) onToggle() },
            )
            Pill(text = "−", onClick = { if (enabled) onBpmChange((bpm - 1).coerceAtLeast(MIN_BPM)) })
            Text(
                "$bpm BPM",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(76.dp),
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
            Pill(text = "$beatsPerBar/4", onClick = {
                if (!enabled) return@Pill
                val i = BEATS_PER_BAR_OPTIONS.indexOf(beatsPerBar)
                onBeatsPerBarChange(BEATS_PER_BAR_OPTIONS[(i + 1) % BEATS_PER_BAR_OPTIONS.size])
            })
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
}

@Composable
private fun Pill(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(if (active) Color(0xFF2E7D32) else Color(0xFF2A2A2A), RoundedCornerShape(17.dp))
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
