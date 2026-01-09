package com.example.geoalert

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.geoalert.ui.MapScreen
import com.example.geoalert.ui.theme.GeoAlertTheme
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint

class GeoAlertActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private val prefs by lazy {
        getSharedPreferences("geo_prefs", MODE_PRIVATE)
    }

    // Mutable state for live location
    private var liveLocationState: MutableState<GeoPoint?> = mutableStateOf(null)

    private val fenceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Receiver logic
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        geofencingClient = LocationServices.getGeofencingClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            fenceStatusReceiver,
            IntentFilter("GEOFENCE_STATUS")
        )

        checkLocationPermission()
        startLiveLocationUpdates()

        setContent {
            GeoAlertTheme {
                GeoAlertScreen(
                    onGeofenceAdd = { lat, lng, radius ->
                        addGeofence(lat, lng, radius)
                    },
                    onGeofenceRemove = {
                        geofencingClient.removeGeofences(listOf("USER_GEOFENCE"))
                    },
                    fusedLocationClient = fusedLocationClient,
                    liveLocationState = liveLocationState
                )
            }
        }
    }

    // ===== Live Location =====
    private fun startLiveLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000
        ).build()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    // Update the mutable state with new location
                    liveLocationState.value = GeoPoint(loc.latitude, loc.longitude)
                }
            },
            Looper.getMainLooper()
        )
    }

    // ===== Permissions =====
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    // ===== Geofence =====
    private fun addGeofence(lat: Double, lng: Double, radius: Float) {
        val geofence = Geofence.Builder()
            .setRequestId("USER_GEOFENCE")
            .setCircularRegion(lat, lng, radius)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(this, GeofenceReceiver::class.java)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            pendingFlags
        )

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            geofencingClient.addGeofences(request, pendingIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(fenceStatusReceiver)
    }
}

@Composable
fun GeoAlertScreen(
    onGeofenceAdd: (Double, Double, Float) -> Unit,
    onGeofenceRemove: () -> Unit,
    fusedLocationClient: FusedLocationProviderClient,
    liveLocationState: MutableState<GeoPoint?>
) {
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var radius by remember { mutableStateOf(200f) }
    var fenceStatus by remember { mutableStateOf("Tap on map to select location") }
    var liveStatus by remember { mutableStateOf("UNKNOWN") }

    // Observe live location from the activity's state
    val liveLocation = liveLocationState.value

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🗺 MAP
            Box(modifier = Modifier.weight(1f)) {
                MapScreen(
                    selectedPoint = selectedPoint,
                    radius = radius,
                    liveLocation = liveLocation,
                    onMapTap = { point: GeoPoint ->
                        selectedPoint = point
                        fenceStatus = "Location selected: ${point.latitude}, ${point.longitude}"
                    }
                )
            }

            // 📍 UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "📍 GeoFence Status",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (liveStatus) {
                        "INSIDE" -> "🟢 Patient is INSIDE Safe Zone"
                        "OUTSIDE" -> "🔴 Patient is OUTSIDE Safe Zone"
                        else -> "⚪ Status unknown"
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                
                Text(text = fenceStatus)

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Radius: ${radius.toInt()} meters")
                
                Slider(
                    value = radius,
                    onValueChange = { newRadius: Float ->
                        radius = newRadius
                    },
                    valueRange = 100f..1000f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (selectedPoint == null) {
                            fenceStatus = "❌ Tap on map first"
                            return@Button
                        }

                        onGeofenceAdd(
                            selectedPoint!!.latitude,
                            selectedPoint!!.longitude,
                            radius
                        )

                        fenceStatus = "✅ Geofence activated"
                    }
                ) {
                    Text("Save / Activate Geofence")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onGeofenceRemove()
                        fenceStatus = "🗑 Geofence cleared"
                    }
                ) {
                    Text("Clear Geofence")
                }
            }
        }
    }
}
