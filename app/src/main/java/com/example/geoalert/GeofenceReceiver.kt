package com.example.geoalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val event = GeofencingEvent.fromIntent(intent)
        if (event?.hasError() == true) return

        val transition = event?.geofenceTransition ?: return
        val status = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "INSIDE"
            Geofence.GEOFENCE_TRANSITION_EXIT  -> "OUTSIDE"
            else -> "UNKNOWN"
        }

        val prefs = context.getSharedPreferences("geo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("live_status", status).apply()

        val b = Intent("GEOFENCE_STATUS").putExtra("status", status)
        LocalBroadcastManager.getInstance(context).sendBroadcast(b)
    }
}
