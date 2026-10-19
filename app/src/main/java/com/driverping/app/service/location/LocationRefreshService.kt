package com.driverping.app.service.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.driverping.app.util.NotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * LocationRefreshService ("Location Spoofer Inverso" / Refresco GPS de Alta Precisión)
 * -----------------------------------------------------------------------------------
 * Solicita actualizaciones GPS de alta precisión cada 2 segundos mientras el Modo Trabajo
 * está activo para forzar al módem GPS del teléfono a reportar coordenadas exactas
 * en tiempo real a las plataformas de viaje (Yummy, Yango, Ridery) sin entrar en reposo.
 */
class LocationRefreshService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isRefreshing = false

    companion object {
        private const val TAG = "LocationRefreshService"
        const val ACTION_START_LOCATION_REFRESH = "com.driverping.action.START_LOCATION_REFRESH"
        const val ACTION_STOP_LOCATION_REFRESH = "com.driverping.action.STOP_LOCATION_REFRESH"
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val FASTEST_INTERVAL_MS = 1000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_LOCATION_REFRESH -> {
                stopLocationUpdates()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!hasLocationPermission()) {
                    Log.w(TAG, "Permisos de ubicación no concedidos. Cancelando LocationRefreshService...")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundCompat()
                startLocationUpdates()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (isRefreshing || !hasLocationPermission()) return

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    Log.d(TAG, "GPS Refrescado: Lat=${location.latitude}, Lon=${location.longitude}, Acc=${location.accuracy}m")
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isRefreshing = true
            Log.i(TAG, "Refresco de GPS de Alta Precisión activo (intervalo 2s)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisos de ubicación no concedidos para GPS Refresh", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar actualizaciones de ubicación", e)
        }
    }

    private fun stopLocationUpdates() {
        if (!isRefreshing) return
        isRefreshing = false
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
            Log.i(TAG, "Refresco de GPS detenido limpiamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al remover actualizaciones de ubicación", e)
        } finally {
            locationCallback = null
        }
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.ensureNotificationChannel(
                this,
                NotificationHelper.CHANNEL_HUD,
                "Refresco GPS",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
        }
        val notif = NotificationHelper.buildForegroundNotification(
            this,
            NotificationHelper.CHANNEL_HUD,
            "Refresco GPS Activo",
            "Manteniendo módem GPS en alta precisión (2s)"
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIF_HUD_ID + 100,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NotificationHelper.NOTIF_HUD_ID + 100, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar startForeground en LocationRefreshService", e)
            stopSelf()
        }
    }
}
