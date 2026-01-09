package com.example.geoalert.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun MapScreen(
    selectedPoint: GeoPoint?,
    radius: Float,
    liveLocation: GeoPoint?,
    onMapTap: (GeoPoint) -> Unit
) {
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(context, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Map View
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    
                    // Set initial location
                    val startPoint = liveLocation ?: GeoPoint(37.7749, -122.4194) // Default: San Francisco
                    controller.setCenter(startPoint)
                    controller.setZoom(15.0)
                    
                    // Add overlay for geofence circle and markers
                    val overlays = overlays
                    
                    // Draw selected point marker
                    if (selectedPoint != null) {
                        controller.setCenter(selectedPoint)
                    }
                }
            },
            update = { mapView ->
                // Update map when properties change
                if (selectedPoint != null) {
                    mapView.controller.setCenter(selectedPoint)
                } else if (liveLocation != null) {
                    mapView.controller.setCenter(liveLocation)
                }
            }
        )

        // Info Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Text("📍 Current Location", style = MaterialTheme.typography.labelMedium)
            Text("Live: ${liveLocation?.latitude?.toInt() ?: "unknown"}, ${liveLocation?.longitude?.toInt() ?: ""}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Selected: ${selectedPoint?.latitude?.toInt() ?: "none"}, ${selectedPoint?.longitude?.toInt() ?: ""}", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.labelSmall)
        }
    }
}
