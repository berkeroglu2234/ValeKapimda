package com.valekapimda.customer

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class CustomerTrackingService : Service() {
    companion object {
        const val ACTION_START = "com.valekapimda.customer.START_TRACKING"
        const val ACTION_STOP = "com.valekapimda.customer.STOP_TRACKING"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_OPEN_TRACKING = "open_tracking"
        private const val CHANNEL_ID = "customer_live_tracking"
        private const val NOTIFICATION_ID = 4201
        fun start(context: Context, requestId: String, phone: String) {
            CustomerTrackingStore.save(context, requestId, phone)
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, CustomerTrackingService::class.java).setAction(ACTION_START).putExtra(EXTRA_REQUEST_ID, requestId).putExtra(EXTRA_PHONE, phone))
        }
        fun stop(context: Context) {
            CustomerTrackingStore.clear(context)
            context.startService(Intent(context, CustomerTrackingService::class.java).setAction(ACTION_STOP))
        }
    }

    private var socket: Socket? = null
    private var requestId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { connect(true) }
    }

    override fun onCreate() {
        super.onCreate(); createChannel()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopTracking(); stopSelf(); return START_NOT_STICKY }
        requestId = intent?.getStringExtra(EXTRA_REQUEST_ID) ?: CustomerTrackingStore.requestId(this)
        val id = requestId
        if (id.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification("Valenizin konumu canlı olarak takip ediliyor"))
        connect()
        return START_STICKY
    }

    private fun connect(force: Boolean = false) {
        if (socket == null) {
            socket = IO.socket("https://valekapimda-api.onrender.com", IO.Options().apply {
                reconnection = true; reconnectionAttempts = Int.MAX_VALUE; reconnectionDelay = 1_000; reconnectionDelayMax = 10_000; timeout = 30_000
            })
            socket?.on(Socket.EVENT_CONNECT) { requestId?.let { socket?.emit("request:join", JSONObject().put("requestId", it)); socket?.emit("request:join", it) } }
            socket?.on("request:updated") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                if (data.optString("id") == requestId) {
                    val status = data.optString("status")
                    if (status == "COMPLETED" || status == "CANCELLED") { stopTracking(); stopSelf() }
                    else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(statusText(status)))
                }
            }
        }
        if (force || socket?.connected() != true) socket?.connect()
    }

    private fun statusText(status: String) = when(status) {
        "SEARCHING" -> "Size uygun vale aranıyor"
        "ASSIGNED" -> "Vale atandı ve hazırlanıyor"
        "DRIVER_EN_ROUTE" -> "Vale alım noktasına geliyor"
        "VEHICLE_PICKED_UP" -> "Aracınız teslim alındı"
        "ON_THE_WAY" -> "Aracınız varış noktasına götürülüyor"
        else -> "Vale hizmetiniz devam ediyor"
    }

    private fun notification(text: String): Notification {
        val launch = Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_TRACKING, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 11, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("ValeKapımda Canlı Takip").setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(pending).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    private fun stopTracking() {
        socket?.disconnect(); socket?.close(); socket = null; CustomerTrackingStore.clear(this); stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { try { connectivityManager?.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}; socket?.disconnect(); socket?.close(); super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Canlı vale takibi", NotificationManager.IMPORTANCE_LOW)) }
    override fun onBind(intent: Intent?): IBinder? = null
}
