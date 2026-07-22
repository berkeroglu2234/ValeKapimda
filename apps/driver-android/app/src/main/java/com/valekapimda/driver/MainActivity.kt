package com.valekapimda.driver

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val DRIVER_DARK_MAP_JSON = """[{"elementType":"geometry","stylers":[{"color":"#101419"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#8f9aa8"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#101419"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#252b33"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#07111d"}]}]"""

private const val API_BASE_URL = "https://valekapimda-api.onrender.com"

private data class DriverRequest(
    val id: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val distanceKm: Double,
    val quotedPrice: Double,
    val status: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val customerName: String = "Müşteri",
    val customerPhone: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriverApp() }
    }
}

@Composable
fun DriverApp() {
    val scope = rememberCoroutineScope()
    val requests = remember { mutableStateListOf<DriverRequest>() }
    var activeRequest by remember { mutableStateOf<DriverRequest?>(null) }

    var available by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Sunucuya bağlanılıyor...") }
    var token by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var locationPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locationPermission = it }

    val socket = remember {
        IO.socket(
            API_BASE_URL,
            IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1_000
                reconnectionDelayMax = 10_000
                timeout = 30_000
                forceNew = false
                // Transport seçimini Socket.IO'ya bırakıyoruz.
                // Önce polling ile bağlanıp uygun olduğunda WebSocket'e yükseltir.
            }
        )
    }

    fun addOrUpdateRequest(json: JSONObject) {
        val item = jsonToDriverRequest(json)
        val index = requests.indexOfFirst { it.id == item.id }

        if (item.status == "SEARCHING") {
            if (activeRequest == null && available) {
                if (index >= 0) requests[index] = item else requests.add(0, item)
            }
            return
        }

        if (index >= 0) requests.removeAt(index)

        if (activeRequest?.id == item.id) {
            if (item.status == "COMPLETED" || item.status == "CANCELLED") {
                activeRequest = null
                available = true
                message = if (item.status == "COMPLETED") {
                    "Görev tamamlandı. Yeni talepleri alabilirsiniz."
                } else {
                    "Görev iptal edildi. Yeni talepleri alabilirsiniz."
                }
            } else {
                activeRequest = item
                available = false
                message = driverStatusText(item.status)
            }
        }
    }

    DisposableEffect(socket) {
        val connectListener = {
            scope.launch {
                connected = true
                message = "Canlı bağlantı kuruldu."
            }
        }

        val disconnectListener = {
            scope.launch {
                connected = false
                message = "Canlı bağlantı kesildi. Yeniden bağlanılıyor..."
            }
        }

        val connectErrorListener: (Array<Any>) -> Unit = { args ->
            val error = args.firstOrNull()?.toString().orEmpty()
            Log.e("ValeDriverSocket", error)
            scope.launch {
                connected = false
                message = "Bağlantı kurulamadı, yeniden deneniyor: $error"
            }
        }

        val newRequestListener: (Array<Any>) -> Unit = { args ->
            val json = args.firstOrNull() as? JSONObject
            if (json != null && available) {
                scope.launch {
                    addOrUpdateRequest(json)
                    message = "Yeni vale talebi geldi."
                }
            }
        }

        val updatedRequestListener: (Array<Any>) -> Unit = { args ->
            val json = args.firstOrNull() as? JSONObject
            if (json != null) {
                scope.launch { addOrUpdateRequest(json) }
            }
        }

        socket.on(Socket.EVENT_CONNECT) { connectListener() }
        socket.on(Socket.EVENT_DISCONNECT) { disconnectListener() }
        socket.on(Socket.EVENT_CONNECT_ERROR, connectErrorListener)
        socket.on("request:new", newRequestListener)
        socket.on("request:updated", updatedRequestListener)
        socket.connect()

        onDispose {
            socket.off(Socket.EVENT_CONNECT)
            socket.off(Socket.EVENT_DISCONNECT)
            socket.off(Socket.EVENT_CONNECT_ERROR)
            socket.off("request:new")
            socket.off("request:updated")
            socket.disconnect()
            socket.close()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val loginResult = driverLogin()

        loginResult.onSuccess { newToken ->
            token = newToken
            val listResult = getSearchingRequests(newToken)

            listResult.onSuccess { list ->
                requests.clear()
                requests.addAll(list)
                loading = false
                message = if (list.isEmpty()) {
                    "Yeni talep bekleniyor..."
                } else {
                    "${list.size} açık talep bulundu."
                }
            }.onFailure {
                loading = false
                message = "Talepler alınamadı: ${it.message}"
            }
        }.onFailure {
            loading = false
            message = "Sürücü girişi başarısız: ${it.message}"
        }
    }


    LaunchedEffect(activeRequest?.id, locationPermission) {
        val requestId = activeRequest?.id ?: return@LaunchedEffect
        if (!locationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        while (activeRequest?.id == requestId) {
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLocation = LatLng(loc.latitude, loc.longitude)
                    }
                    if (loc != null && socket.connected()) {
                        socket.emit("location:update", JSONObject().apply {
                            put("requestId", requestId)
                            put("lat", loc.latitude)
                            put("lng", loc.longitude)
                            put("timestamp", System.currentTimeMillis())
                        })
                    }
                }
            } catch (_: SecurityException) { }
            delay(5_000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = Color(0xFFFF9800))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0F14)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(18.dp))

                Text(
                    "ValeKapımda Vale",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (connected) "● Canlı bağlantı aktif" else "● Bağlantı bekleniyor",
                    color = if (connected) Color(0xFF55D98A) else Color(0xFFFFB74D),
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (available) "Müsait" else "Meşgul",
                        color = if (available) Color(0xFF55D98A) else Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Bold
                    )

                    Switch(
                        checked = available,
                        enabled = activeRequest == null,
                        onCheckedChange = {
                            available = it
                            message = if (it) {
                                "Yeni talepler gösterilecek."
                            } else {
                                "Çevrimdışısınız; yeni talepler gösterilmeyecek."
                            }
                        }
                    )
                }

                Text(
                    message,
                    color = Color(0xFF9AA4B2),
                    fontSize = 13.sp
                )

                val currentActiveRequest = activeRequest

                if (loading) {
                    CircularProgressIndicator()
                } else if (currentActiveRequest != null) {
                    DriverNavigationScreen(
                        request = currentActiveRequest,
                        currentLocation = currentLocation,
                        enabled = token != null,
                        onAdvance = { nextStatus ->
                            val currentToken = token
                            if (currentToken == null) {
                                message = "Oturum henüz hazır değil."
                            } else {
                                scope.launch {
                                    message = "Durum güncelleniyor..."

                                    updateRequestStatus(
                                        token = currentToken,
                                        requestId = currentActiveRequest.id,
                                        status = nextStatus
                                    ).onSuccess { updated ->
                                        activeRequest = if (
                                            updated.status == "COMPLETED" ||
                                            updated.status == "CANCELLED"
                                        ) {
                                            available = true
                                            null
                                        } else {
                                            available = false
                                            updated
                                        }
                                        message = driverStatusText(updated.status)
                                    }.onFailure {
                                        message = "Durum güncellenemedi: ${it.message}"
                                    }
                                }
                            }
                        },
                        onCancel = {
                            val currentToken = token
                            if (currentToken == null) {
                                message = "Oturum henüz hazır değil."
                            } else {
                                scope.launch {
                                    message = "Görev iptal ediliyor..."

                                    updateRequestStatus(
                                        token = currentToken,
                                        requestId = currentActiveRequest.id,
                                        status = "CANCELLED"
                                    ).onSuccess {
                                        activeRequest = null
                                        available = true
                                        message = "Görev iptal edildi."
                                    }.onFailure {
                                        message = "Görev iptal edilemedi: ${it.message}"
                                    }
                                }
                            }
                        }
                    )
                } else if (!available) {
                    EmptyCard("Çevrimdışısınız. Yeni talep almak için Müsait anahtarını açın.")
                } else if (requests.isEmpty()) {
                    EmptyCard("Yeni vale talebi bekleniyor...")
                } else {
                    requests.forEach { request ->
                        RequestCard(
                            request = request,
                            enabled = token != null,
                            onReject = {
                                requests.removeAll { it.id == request.id }
                                message = "Talep bu cihazda gizlendi."
                            },
                            onAccept = {
                                val currentToken = token
                                if (currentToken == null) {
                                    message = "Oturum henüz hazır değil."
                                } else {
                                    scope.launch {
                                        message = "Talep kabul ediliyor..."

                                        acceptRequest(currentToken, request.id)
                                            .onSuccess { accepted ->
                                                requests.removeAll { it.id == request.id }
                                                activeRequest = accepted
                                                available = false
                                                message = driverStatusText(accepted.status)
                                            }
                                            .onFailure {
                                                message = "Kabul edilemedi: ${it.message}"
                                            }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: DriverRequest,
    enabled: Boolean,
    onReject: () -> Unit,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B23)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Yeni Talep",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "📍 ${request.pickupAddress}",
                color = Color.White
            )

            Text(
                "🏁 ${request.destinationAddress}",
                color = Color.White
            )

            Text(
                "Mesafe: %.1f km".format(request.distanceKm),
                color = Color(0xFF9AA4B2)
            )

            Text(
                "Tahmini kazanç: ₺${request.quotedPrice.toInt()}",
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reddet")
                }

                Button(
                    onClick = onAccept,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Kabul Et")
                }
            }
        }
    }
}


@Composable
private fun DriverNavigationScreen(
    request: DriverRequest,
    currentLocation: LatLng?,
    enabled: Boolean,
    onAdvance: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val pickup = LatLng(request.pickupLat, request.pickupLng)
    val destination = LatLng(request.destinationLat, request.destinationLng)
    val target = if (request.status in listOf("IN_TRANSIT", "DELIVERED")) destination else pickup
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(currentLocation ?: target, 15f) }
    var mapReady by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var eta by remember { mutableStateOf(0) }
    var km by remember { mutableStateOf(0.0) }
    val next = nextDriverStatus(request.status)

    LaunchedEffect(currentLocation, target) {
        val from = currentLocation ?: return@LaunchedEffect
        fetchDriverRoute(from, target).onSuccess { info -> route = info.first; km = info.second; eta = info.third }
        if (mapReady) runCatching {
            val b = com.google.android.gms.maps.model.LatLngBounds.builder().include(from).include(target).build()
            camera.animate(CameraUpdateFactory.newLatLngBounds(b, 150))
        }
    }

    Box(Modifier.fillMaxWidth().height(720.dp)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isTrafficEnabled = true, mapStyleOptions = MapStyleOptions(DRIVER_DARK_MAP_JSON)),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true),
            onMapLoaded = { mapReady = true }
        ) {
            Marker(state = rememberMarkerState(position = pickup), title = "Müşteri")
            Marker(state = rememberMarkerState(position = destination), title = "Teslim noktası")
            currentLocation?.let { Marker(state = rememberMarkerState(position = it), title = "Konumunuz") }
            route.takeIf { it.size > 1 }?.let { Polyline(points = it, color = Color(0xFF2F80ED), width = 14f) }
        }

        Card(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE151B23)), shape = RoundedCornerShape(22.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(driverStatusText(request.status), color = Color.White, fontWeight = FontWeight.Bold); Text(if (target == pickup) request.pickupAddress else request.destinationAddress, color = Color(0xFF9AA4B2), fontSize = 12.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("${maxOf(1, eta)} dk", color = Color(0xFFFF9800), fontSize = 24.sp, fontWeight = FontWeight.Black); Text("%.1f km".format(km), color = Color(0xFF9AA4B2)) }
            }
        }

        Card(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF510141A)), shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.customerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("📍 ${request.pickupAddress}", color = Color.White, fontSize = 13.sp)
                Text("🏁 ${request.destinationAddress}", color = Color.White, fontSize = 13.sp)
                Button(onClick = {
                    val uri = Uri.parse("google.navigation:q=${target.latitude},${target.longitude}&mode=d")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") })
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Navigasyonu Başlat") }
                if (next != null) Button(onClick = { onAdvance(next.first) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), shape = RoundedCornerShape(16.dp)) { Text(next.second) }
                OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Görevi İptal Et") }
            }
        }
    }
}

@Composable
private fun ActiveRequestCard(
    request: DriverRequest,
    enabled: Boolean,
    onAdvance: (String) -> Unit,
    onCancel: () -> Unit
) {
    val next = nextDriverStatus(request.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B23)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Aktif Görev",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                driverStatusText(request.status),
                color = Color(0xFF55D98A),
                fontWeight = FontWeight.Bold
            )

            Text("📍 ${request.pickupAddress}", color = Color.White)
            Text("🏁 ${request.destinationAddress}", color = Color.White)
            Text(
                "Mesafe: %.1f km".format(request.distanceKm),
                color = Color(0xFF9AA4B2)
            )
            Text(
                "Tahmini kazanç: ₺${request.quotedPrice.toInt()}",
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Bold
            )

            if (next != null) {
                Button(
                    onClick = { onAdvance(next.first) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text(next.second)
                }
            }

            OutlinedButton(
                onClick = onCancel,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Görevi İptal Et")
            }
        }
    }
}

private fun nextDriverStatus(status: String): Pair<String, String>? {
    return when (status) {
        "ASSIGNED" -> "DRIVER_EN_ROUTE" to "Müşteriye Doğru Yola Çıktım"
        "DRIVER_EN_ROUTE" -> "ARRIVED" to "Alım Noktasına Geldim"
        "ARRIVED" -> "VEHICLE_RECEIVED" to "Aracı Teslim Aldım"
        "VEHICLE_RECEIVED" -> "IN_TRANSIT" to "Teslimat İçin Yola Çıktım"
        "IN_TRANSIT" -> "DELIVERED" to "Aracı Teslim Ettim"
        "DELIVERED" -> "COMPLETED" to "Görevi Tamamla"
        else -> null
    }
}

private fun driverStatusText(status: String): String {
    return when (status) {
        "ASSIGNED" -> "Talep kabul edildi. Yola çıkmanız bekleniyor."
        "DRIVER_EN_ROUTE" -> "Müşteriye doğru yoldasınız."
        "ARRIVED" -> "Alım noktasına ulaştınız."
        "VEHICLE_RECEIVED" -> "Araç teslim alındı."
        "IN_TRANSIT" -> "Araç teslimat noktasına götürülüyor."
        "DELIVERED" -> "Araç müşteriye teslim edildi."
        "COMPLETED" -> "Görev tamamlandı."
        "CANCELLED" -> "Görev iptal edildi."
        else -> "Aktif görev devam ediyor."
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B23)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(20.dp),
            color = Color(0xFF9AA4B2)
        )
    }
}

private fun jsonToDriverRequest(json: JSONObject): DriverRequest {
    return DriverRequest(
        id = json.optString("id"),
        pickupAddress = json.optString("pickup_address", "Alım noktası"),
        destinationAddress = json.optString("destination_address", "Varış noktası"),
        distanceKm = json.optDouble(
            "distanceKm",
            json.optDouble("distance_km", 0.0)
        ),    
        quotedPrice = json.optDouble(
            "quotedPrice",
            json.optDouble("quoted_price", 0.0)
        ),
        status = json.optString("status", "SEARCHING"),
        pickupLat = json.optDouble("pickup_lat", 0.0),
        pickupLng = json.optDouble("pickup_lng", 0.0),
        destinationLat = json.optDouble("destination_lat", 0.0),
        destinationLng = json.optDouble("destination_lng", 0.0),
        customerName = json.optString("customer_name", "Müşteri"),
        customerPhone = json.optString("customer_phone", "")
    )
}

private suspend fun driverLogin(): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val response = requestJson(
            method = "POST",
            url = "$API_BASE_URL/auth/demo-login",
            body = JSONObject().apply {
                put("role", "DRIVER")
                put("phone", "+905551112233")
                put("fullName", "Demo Vale")
            }
        )

        response.getString("token")
    }
}

private suspend fun getSearchingRequests(
    token: String
): Result<List<DriverRequest>> = withContext(Dispatchers.IO) {
    runCatching {
        val response = requestText(
            method = "GET",
            url = "$API_BASE_URL/requests",
            token = token
        )

        val array = JSONArray(response)
        buildList {
            for (index in 0 until array.length()) {
                val request = jsonToDriverRequest(array.getJSONObject(index))
                if (request.status == "SEARCHING") add(request)
            }
        }
    }
}

private suspend fun acceptRequest(
    token: String,
    requestId: String
): Result<DriverRequest> {
    return updateRequestStatus(
        token = token,
        requestId = requestId,
        status = "ASSIGNED"
    )
}

private suspend fun updateRequestStatus(
    token: String,
    requestId: String,
    status: String
): Result<DriverRequest> = withContext(Dispatchers.IO) {
    runCatching {
        val response = requestJson(
            method = "PATCH",
            url = "$API_BASE_URL/requests/$requestId/status",
            token = token,
            body = JSONObject().apply {
                put("status", status)
            }
        )

        jsonToDriverRequest(response)
    }
}


private suspend fun fetchDriverRoute(from: LatLng, to: LatLng): Result<Triple<List<LatLng>, Double, Int>> = withContext(Dispatchers.IO) {
    runCatching {
        val o = requestJson("GET", "$API_BASE_URL/route?fromLat=${from.latitude}&fromLng=${from.longitude}&toLat=${to.latitude}&toLng=${to.longitude}")
        val a = o.getJSONArray("points")
        val points = (0 until a.length()).map { i -> val p = a.getJSONArray(i); LatLng(p.getDouble(0), p.getDouble(1)) }
        Triple(points, o.optDouble("distanceKm", 0.0), o.optInt("durationMinutes", 0))
    }
}

private fun requestJson(
    method: String,
    url: String,
    token: String? = null,
    body: JSONObject? = null
): JSONObject {
    val response = requestText(method, url, token, body)
    if (response.isBlank()) return JSONObject()
    return JSONObject(response)
}

private fun requestText(
    method: String,
    url: String,
    token: String? = null,
    body: JSONObject? = null
): String {
    val connection = URL(url).openConnection() as HttpURLConnection

    return try {
        connection.requestMethod = method
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.doInput = true
        connection.setRequestProperty("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()

        if (code !in 200..299) {
            throw IllegalStateException("Sunucu $code: $response")
        }

        response
    } finally {
        connection.disconnect()
    }
}
