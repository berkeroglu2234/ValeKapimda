package com.valekapimda.driver

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class DriverLocationService : Service() {
    companion object {
        const val ACTION_START = "com.valekapimda.driver.START_TRACKING"
        const val ACTION_STOP = "com.valekapimda.driver.STOP_TRACKING"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "driver_live_location"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context, requestId: String) {
            DriverTrackingStore.save(context, requestId)
            val i = Intent(context, DriverLocationService::class.java).setAction(ACTION_START).putExtra(EXTRA_REQUEST_ID, requestId)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
        fun stop(context: Context) {
            DriverTrackingStore.clear(context)
            context.startService(Intent(context, DriverLocationService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var fused: FusedLocationProviderClient
    private var socket: Socket? = null
    private var requestId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val id = requestId ?: return
            val location = result.lastLocation ?: return
            ensureSocket()
            socket?.emit("location:update", JSONObject().apply {
                put("requestId", id)
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("accuracy", location.accuracy)
                put("speed", location.speed)
                put("bearing", location.bearing)
                put("timestamp", System.currentTimeMillis())
            })
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { ensureSocket(forceReconnect = true) }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        fused = LocationServices.getFusedLocationProviderClient(this)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ValeKapimda:DriverLocation").apply { acquire() }
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking(); stopSelf(); return START_NOT_STICKY
        }
        requestId = intent?.getStringExtra(EXTRA_REQUEST_ID) ?: DriverTrackingStore.get(this)
        val id = requestId
        if (id.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        DriverTrackingStore.save(this, id)
        startForeground(NOTIFICATION_ID, notification("Aktif görev için konum paylaşılıyor"))
        ensureSocket()
        startLocationUpdates()
        return START_STICKY
    }

    private fun ensureSocket(forceReconnect: Boolean = false) {
        if (socket == null) {
            socket = IO.socket("https://valekapimda-api.onrender.com", IO.Options().apply {
                reconnection = true; reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1_000; reconnectionDelayMax = 10_000; timeout = 30_000
            })
        }
        if (forceReconnect && socket?.connected() == false) socket?.connect()
        else if (socket?.connected() != true) socket?.connect()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L).setMaxUpdateDelayMillis(8_000L).build()
        fused.removeLocationUpdates(locationCallback)
        fused.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun stopTracking() {
        fused.removeLocationUpdates(locationCallback)
        socket?.disconnect(); socket?.close(); socket = null
        DriverTrackingStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        try { connectivityManager?.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        socket?.disconnect(); socket?.close()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("ValeKapımda Vale")
            .setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Canlı konum paylaşımı", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Aktif vale görevi sırasında arka plan konum paylaşımı"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
