package com.fitrater.app.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

@Serializable
private data class OpenMeteo(val current: Current? = null) {
    @Serializable data class Current(val temperature_2m: Double? = null)
}

object Weather {
    private const val TAG = "Weather"
    private val json = Json { ignoreUnknownKeys = true }

    private const val FALLBACK_LAT = 41.01
    private const val FALLBACK_LON = 28.98

    suspend fun temperatureCelsius(context: Context): Int? = withContext(Dispatchers.IO) {
        val (lat, lon) = getLatLon(context) ?: (FALLBACK_LAT to FALLBACK_LON)
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val parsed = json.decodeFromString(OpenMeteo.serializer(), body)
            parsed.current?.temperature_2m?.toInt()
        } catch (e: Exception) {
            Log.w(TAG, "temperatureCelsius failed", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLatLon(context: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            withTimeoutOrNull(2000) {
                val client = LocationServices.getFusedLocationProviderClient(context)
                suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                    client.lastLocation
                        .addOnSuccessListener { loc ->
                            if (loc != null) cont.resume(loc.latitude to loc.longitude)
                            else cont.resume(null)
                        }
                        .addOnFailureListener {
                            Log.w(TAG, "lastLocation failed", it)
                            cont.resume(null)
                        }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getLatLon failed", e)
            null
        }
    }
}
