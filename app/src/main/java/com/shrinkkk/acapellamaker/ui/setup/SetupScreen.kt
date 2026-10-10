package com.shrinkkk.acapellamaker.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shrinkkk.acapellamaker.model.GridLayout
import com.shrinkkk.acapellamaker.model.GridLayouts
import com.shrinkkk.acapellamaker.ui.grid.GridCanvas

/** Pick how many takes, then one of the standard layouts for that count. */
@Composable
fun SetupScreen(onStart: (GridLayout) -> Unit, onOpenCalibration: () -> Unit) {
    var takeCount by rememberSaveable { mutableIntStateOf(4) }
    val options = remember(takeCount) { GridLayouts.optionsFor(takeCount) }
    var selectedLayout by rememberSaveable(takeCount) { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text("New acapella", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            Text("How many takes?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..GridLayouts.MAX_TAKES).forEach { n ->
                    CountChip(n = n, selected = n == takeCount, onClick = { takeCount = n })
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Layout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                options.forEachIndexed { i, layout ->
                    LayoutOption(
                        layout = layout,
                        selected = i == selectedLayout,
                        onClick = { selectedLayout = i },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onOpenCalibration,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Sync calibration")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onStart(options[selectedLayout.coerceIn(options.indices)]) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Start recording")
            }
        }
    }
}

@Composable
private fun CountChip(n: Int, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (selected) primary else Color.Transparent, CircleShape)
            .border(1.dp, if (selected) primary else Color.Gray, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$n",
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A phone-shaped thumbnail with grey cells. */
@Composable
private fun LayoutOption(layout: GridLayout, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(GridLayouts.CANVAS_ASPECT)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) primary else Color.Gray,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(6.dp),
        ) {
            GridCanvas(layout = layout, modifier = Modifier.fillMaxSize(), gap = 3.dp, corner = 3.dp) {
                Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            layout.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
