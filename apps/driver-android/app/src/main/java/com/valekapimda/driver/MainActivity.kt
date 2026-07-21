package com.valekapimda.driver

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_BASE_URL = "http://10.0.2.2:4000"

private data class DriverRequest(
    val id: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val distanceKm: Double,
    val quotedPrice: Double,
    val status: String
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

    var available by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Sunucuya bağlanılıyor...") }
    var token by remember { mutableStateOf<String?>(null) }

    val socket = remember {
        IO.socket(
            API_BASE_URL,
            IO.Options().apply {
                reconnection = true
                forceNew = true
                transports = arrayOf("websocket", "polling")
            }
        )
    }

    fun addOrUpdateRequest(json: JSONObject) {
        val item = jsonToDriverRequest(json)
        val index = requests.indexOfFirst { it.id == item.id }

        if (item.status == "SEARCHING") {
            if (index >= 0) requests[index] = item else requests.add(0, item)
        } else if (index >= 0) {
            requests.removeAt(index)
        }
    }

    DisposableEffect(socket) {
        val connectListener = {
            connected = true
            message = "Canlı bağlantı kuruldu."
        }

        val disconnectListener = {
            connected = false
            message = "Canlı bağlantı kesildi. Yeniden bağlanılıyor..."
        }

        val connectErrorListener: (Array<Any>) -> Unit = { args ->
            connected = false
            val error = args.firstOrNull()?.toString().orEmpty()
            message = "Socket bağlantı hatası: $error"
            Log.e("ValeDriverSocket", error)
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

                if (loading) {
                    CircularProgressIndicator()
                } else if (!available) {
                    EmptyCard("Şu anda meşgulsünüz.")
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
                                            .onSuccess {
                                                requests.removeAll { it.id == request.id }
                                                available = false
                                                message = "Talep kabul edildi. Müşteriye bilgi gönderildi."
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
        distanceKm = json.optDouble("distance_km", 0.0),
        quotedPrice = json.optDouble("quoted_price", 0.0),
        status = json.optString("status", "SEARCHING")
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
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        requestJson(
            method = "PATCH",
            url = "$API_BASE_URL/requests/$requestId/status",
            token = token,
            body = JSONObject().apply {
                put("status", "ASSIGNED")
            }
        )
        Unit
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
