package com.example.geoalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint

@Composable
fun MapScreen(
    selectedPoint: GeoPoint?,
    radius: Float,
    liveLocation: GeoPoint?,
    onMapTap: (GeoPoint) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                val picked = liveLocation ?: GeoPoint(0.0, 0.0)
                onMapTap(picked)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text("Map placeholder", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Live: ${liveLocation?.latitude ?: "unknown"}, ${liveLocation?.longitude ?: ""}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Selected: ${selectedPoint?.latitude ?: "none"}, ${selectedPoint?.longitude ?: ""}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Radius: ${radius.toInt()} m")
        }
    }
}
