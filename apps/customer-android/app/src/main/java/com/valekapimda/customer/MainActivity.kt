package com.valekapimda.customer

import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import com.valekapimda.customer.ui.theme.ValeKapimdaTheme
import com.valekapimda.customer.ui.components.PremiumPrimaryButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.json.JSONArray
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.roundToInt

private val Background = Color(0xFF090D12)
private val SurfaceDark = Color(0xFF141A22)
private val SurfaceSoft = Color(0xFF1B232E)
private val Border = Color(0xFF2A3442)
private val Muted = Color(0xFF98A2B3)
private val Orange = Color(0xFFFF9800)
private val Success = Color(0xFF45D483)
private val Danger = Color(0xFFFF6B6B)

private enum class Screen { Splash, Login, Otp, Home, Vehicles, AddVehicle }
private enum class CustomerTab { Home, History, Profile }


private data class HistoryItem(
    val id: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val distanceKm: Double,
    val quotedPrice: Double,
    val status: String,
    val createdAt: String
)

private data class CustomerProfile(
    val fullName: String,
    val phone: String
)

private data class Vehicle(
    val id: Int,
    val plate: String,
    val brand: String,
    val model: String,
    val color: String,
    val year: String,
    val transmission: String,
    val verified: Boolean = false
)


private data class PlaceSuggestion(val displayName: String, val point: LatLng)
private data class RouteInfo(val distanceKm: Double, val durationMinutes: Int, val points: List<LatLng>)
private data class DriverInfo(val name: String, val phone: String, val rating: Double, val plate: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ValeKapimdaApp() }
    }
}

@Composable
private fun ValeKapimdaApp() {
    var screen by remember { mutableStateOf(Screen.Splash) }
    var phone by remember { mutableStateOf("") }
    val vehicles = remember {
        mutableStateListOf(
            Vehicle(1, "34 VK 2026", "BMW", "520i", "Siyah", "2022", "Otomatik", true)
        )
    }
    var selectedVehicleId by remember { mutableStateOf<Int?>(vehicles.firstOrNull()?.id) }

    ValeKapimdaTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            when (screen) {
                Screen.Splash -> SplashScreen { screen = Screen.Login }
                Screen.Login -> LoginScreen(phone, { phone = it.filter(Char::isDigit).take(10) }) { screen = Screen.Otp }
                Screen.Otp -> OtpScreen(phone, { screen = Screen.Login }) { screen = Screen.Home }
                Screen.Home -> CustomerShell(
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    onSelectVehicle = { selectedVehicleId = it },
                    onOpenVehicles = { screen = Screen.Vehicles },
                    onLogout = { phone = ""; screen = Screen.Login },
                    phone = phone
                )
                Screen.Vehicles -> VehiclesScreen(
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    onSelect = { selectedVehicleId = it },
                    onDelete = { id ->
                        vehicles.removeAll { it.id == id }
                        if (selectedVehicleId == id) { selectedVehicleId = vehicles.firstOrNull()?.id }
                    },
                    onAdd = { screen = Screen.AddVehicle },
                    onBack = { screen = Screen.Home }
                )
                Screen.AddVehicle -> AddVehicleScreen(
                    onBack = { screen = Screen.Vehicles },
                    onSave = { vehicle ->
                        vehicles.add(vehicle.copy(id = (vehicles.maxOfOrNull { it.id } ?: 0) + 1))
                        selectedVehicleId = vehicles.last().id
                        screen = Screen.Vehicles
                    }
                )
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (visible) 1f else 0.72f, label = "logoScale")
    LaunchedEffect(Unit) { visible = true; delay(1400); onFinished() }
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(scale).size(92.dp).background(Orange, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                Text("V", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(22.dp))
            Text("ValeKapımda", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Valeniz tek dokunuşla kapınızda", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LoginScreen(phone: String, onPhoneChanged: (String) -> Unit, onContinue: () -> Unit) {
    val valid = phone.length == 10
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader(); Spacer(Modifier.height(38.dp))
        Text("Hoş geldiniz", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Vale çağırmak için telefon numaranızla güvenli giriş yapın.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(phone, onPhoneChanged, Modifier.fillMaxWidth(), label = { Text("Telefon numarası") }, prefix = { Text("+90  ", color = Color.White) }, placeholder = { Text("5XX XXX XX XX") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(16.dp), colors = fieldColors())
        AnimatedVisibility(phone.isNotEmpty() && !valid) { Text("Numarayı başında 0 olmadan 10 hane yazın.", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        Spacer(Modifier.height(20.dp)); PrimaryButton("Doğrulama kodu gönder", valid, onContinue)
        Spacer(Modifier.height(18.dp)); Text("Devam ederek Kullanım Koşulları ve KVKK Aydınlatma Metni'ni kabul etmiş olursunuz.", color = Color(0xFF6F7A89), fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp)); SecurityCard()
    }
}

@Composable
private fun OtpScreen(phone: String, onBack: () -> Unit, onVerified: () -> Unit) {
    var code by remember { mutableStateOf("") }; var seconds by remember { mutableStateOf(30) }
    BackHandler(onBack = onBack)
    LaunchedEffect(seconds) { if (seconds > 0) { delay(1000); seconds -= 1 } }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        Text("‹ Geri", color = Orange, modifier = Modifier.clickable(onClick = onBack)); Spacer(Modifier.height(30.dp)); BrandHeader(); Spacer(Modifier.height(38.dp))
        Text("SMS doğrulama", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("+90 ${formatPhone(phone)} numarasına gönderilen 6 haneli kodu girin.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(26.dp)); OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Doğrulama kodu") }, placeholder = { Text("• • • • • •") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = RoundedCornerShape(16.dp), colors = fieldColors())
        Spacer(Modifier.height(20.dp)); PrimaryButton("Girişi tamamla", code.length == 6, onVerified); Spacer(Modifier.height(20.dp))
        Text(if (seconds > 0) "Yeni kodu $seconds saniye sonra gönderebilirsiniz." else "Kodu yeniden gönder", color = if (seconds > 0) Muted else Orange, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(enabled = seconds == 0) { seconds = 30; code = "" })
    }
}

@Composable
private fun CustomerShell(
    vehicles: List<Vehicle>,
    selectedVehicleId: Int?,
    onSelectVehicle: (Int) -> Unit,
    onOpenVehicles: () -> Unit,
    onLogout: () -> Unit,
    phone: String
) {
    var selectedTab by remember { mutableStateOf(CustomerTab.Home) }
    var trackingActive by remember { mutableStateOf(false) }

    BackHandler(enabled = trackingActive) { }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                CustomerTab.Home -> HomeScreen(
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicleId,
                    onSelectVehicle = onSelectVehicle,
                    onOpenVehicles = onOpenVehicles,
                    onLogout = onLogout,
                    phone = phone,
                    onTrackingChanged = { active ->
                        trackingActive = active
                        if (active) selectedTab = CustomerTab.Home
                    }
                )
                CustomerTab.History -> HistoryScreen(phone = phone)
                CustomerTab.Profile -> ProfileScreen(
                    phone = phone,
                    vehicleCount = vehicles.size,
                    onOpenVehicles = onOpenVehicles,
                    onLogout = onLogout
                )
            }
        }

        if (!trackingActive) NavigationBar(containerColor = SurfaceDark) {
            NavigationBarItem(
                selected = selectedTab == CustomerTab.Home,
                onClick = { selectedTab = CustomerTab.Home },
                icon = { Text("⌂", fontSize = 21.sp) },
                label = { Text("Ana Sayfa") }
            )
            NavigationBarItem(
                selected = selectedTab == CustomerTab.History,
                onClick = { selectedTab = CustomerTab.History },
                icon = { Text("↺", fontSize = 21.sp) },
                label = { Text("Geçmiş") }
            )
            NavigationBarItem(
                selected = selectedTab == CustomerTab.Profile,
                onClick = { selectedTab = CustomerTab.Profile },
                icon = { Text("●", fontSize = 17.sp) },
                label = { Text("Profil") }
            )
        }
    }
}

@Composable
private fun HistoryScreen(phone: String) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val items = remember { mutableStateListOf<HistoryItem>() }

    suspend fun loadHistory() {
        loading = true
        error = null
        val result = fetchCustomerHistory(phone)
        result.onSuccess { list ->
            items.clear()
            items.addAll(list)
        }.onFailure { error = it.message ?: "Geçmiş yüklenemedi." }
        loading = false
    }

    LaunchedEffect(phone, refreshKey) { loadHistory() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("İşlem Geçmişi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Tamamlanan ve devam eden vale talepleriniz", color = Muted, fontSize = 14.sp)

        when {
            loading -> StatusCard("Geçmişiniz yükleniyor...")
            error != null -> {
                StatusCard(error!!)
                PrimaryButton("Tekrar dene", true) { refreshKey += 1 }
            }
            items.isEmpty() -> StatusCard("Henüz bir vale işleminiz bulunmuyor.")
            else -> items.forEach { item -> HistoryCard(item) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HistoryCard(item: HistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(requestStatusLabel(item.status), color = if (item.status == "COMPLETED") Success else Orange, fontWeight = FontWeight.Bold)
                Text("₺${item.quotedPrice.roundToInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("📍 ${item.pickupAddress}", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            Text("🏁 ${item.destinationAddress}", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${"%.1f".format(item.distanceKm)} km", color = Muted, fontSize = 12.sp)
                Text(formatServerDate(item.createdAt), color = Muted, fontSize = 12.sp)
            }
            Text("Talep: ${item.id.take(8).uppercase()}", color = Color(0xFF6F7A89), fontSize = 11.sp)
        }
    }
}

@Composable
fun ProfileScreen(
    phone: String,
    vehicleCount: Int,
    onOpenVehicles: () -> Unit,
    onLogout: () -> Unit
) {
    var profile by remember { mutableStateOf<CustomerProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(phone) {
        fetchCustomerProfile(phone).onSuccess { profile = it }.onFailure { error = it.message }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Profilim", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Hesap ve araç bilgilerinizi yönetin", color = Muted, fontSize = 14.sp)

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(74.dp).background(Orange, CircleShape), contentAlignment = Alignment.Center) {
                    Text("V", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text(profile?.fullName ?: if (loading) "Yükleniyor..." else "ValeKapımda Müşterisi", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(profile?.phone ?: "+90${phone.filter(Char::isDigit).takeLast(10)}", color = Muted, fontSize = 14.sp)
                if (error != null) Text(error!!, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kayıtlı araçlar", color = Color.White)
                    Text(vehicleCount.toString(), color = Orange, fontWeight = FontWeight.Bold)
                }
                PrimaryButton("Araçlarımı yönet", true, onOpenVehicles)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Güvenlik", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Telefon doğrulamasıyla korunan müşteri hesabı", color = Muted, fontSize = 13.sp)
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2024)),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Hesaptan çıkış yap", color = Danger, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Text(text, color = Muted, modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeScreen(
    vehicles: List<Vehicle>,
    selectedVehicleId: Int?,
    onSelectVehicle: (Int) -> Unit,
    onOpenVehicles: () -> Unit,
    onLogout: () -> Unit,
    phone: String,
    onTrackingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = vehicles.firstOrNull { it.id == selectedVehicleId }

    var pickupPoint by remember { mutableStateOf<LatLng?>(null) }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }
    var pickup by remember { mutableStateOf("Konumunuz alınıyor...") }
    var destinationQuery by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("Nereye gidilecek?") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searchingPlaces by remember { mutableStateOf(false) }
    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Konumunuz hazırlanıyor") }
    var sending by remember { mutableStateOf(false) }
    var activeRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestStatus by remember { mutableStateOf("IDLE") }
    var socketConnected by remember { mutableStateOf(false) }
    var driverPoint by remember { mutableStateOf<LatLng?>(null) }
    var driverInfo by remember { mutableStateOf<DriverInfo?>(null) }
    var showDestinationSearch by remember { mutableStateOf(false) }

    val trackingActive = activeRequestId != null && requestStatus !in listOf("COMPLETED", "CANCELLED")
    LaunchedEffect(trackingActive) { onTrackingChanged(trackingActive) }
    BackHandler(enabled = trackingActive) { }

    val latestRequestId by rememberUpdatedState(activeRequestId)
    val socket = remember {
        IO.socket(API_BASE_URL, IO.Options().apply {
            transports = arrayOf("websocket", "polling")
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1_000
            timeout = 30_000
        })
    }

    suspend fun refreshRequestDetails(requestId: String) {
        fetchRequestDetails(phone, requestId).onSuccess { details ->
            requestStatus = details.optString("status", requestStatus)
            val name = details.optString("driver_name", details.optJSONObject("driver")?.optString("full_name") ?: "")
            val driverPhone = details.optString("driver_phone", details.optJSONObject("driver")?.optString("phone") ?: "")
            val rating = details.optDouble("driver_rating", details.optJSONObject("driver")?.optDouble("rating", 5.0) ?: 5.0)
            if (name.isNotBlank()) driverInfo = DriverInfo(name, driverPhone, rating, selected?.plate ?: "")
        }
    }
    LaunchedEffect(activeRequestId) {
        activeRequestId?.let {
            refreshRequestDetails(requestId = it)
        }
    }


    DisposableEffect(socket) {
        val connectListener = io.socket.emitter.Emitter.Listener {
            scope.launch {
                socketConnected = true
                latestRequestId?.let {
                    socket.emit(
                        "request:join",
                        JSONObject().put("requestId", it)
                    )
                }
            }
        }
        val disconnectListener = io.socket.emitter.Emitter.Listener { scope.launch { socketConnected = false } }
        val requestUpdatedListener = io.socket.emitter.Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@Listener
            if (data.optString("id") == latestRequestId) {
                scope.launch {
                    requestStatus = data.optString("status", requestStatus)
                    message = requestStatusText(requestStatus)
                    latestRequestId?.let { refreshRequestDetails(it) }
                    if (requestStatus in listOf("COMPLETED", "CANCELLED")) {
                        activeRequestId = null
                        driverPoint = null
                        driverInfo = null
                    }
                }
            }
        }
        val locationListener = io.socket.emitter.Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@Listener
            if (data.optString("requestId") == latestRequestId) {
                val lat = data.optDouble("lat", Double.NaN)
                val lng = data.optDouble("lng", Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) scope.launch { driverPoint = LatLng(lat, lng) }
            }
        }
        socket.on(Socket.EVENT_CONNECT, connectListener)
        socket.on(Socket.EVENT_DISCONNECT, disconnectListener)
        socket.on("request:updated", requestUpdatedListener)
        socket.on("location:updated", locationListener)
        socket.connect()
        if (latestRequestId != null) {
            socket.emit(
                "request:join",
                JSONObject().put("requestId", latestRequestId)
            )
        }
        onDispose {
            socket.off(Socket.EVENT_CONNECT, connectListener)
            socket.off(Socket.EVENT_DISCONNECT, disconnectListener)
            socket.off("request:updated", requestUpdatedListener)
            socket.off("location:updated", locationListener)
            socket.disconnect()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.0082, 28.9784), 11f)
    }

    fun applyPickup(point: LatLng) {
        pickupPoint = point
        pickup = coordinateLabel("Mevcut konum", point)
        scope.launch {
            reverseGeocode(point).onSuccess { pickup = it }.onFailure { message = "Adres bilgisi alınamadı." }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation(context) { point, error -> if (point != null) applyPickup(point) else message = error ?: "Konum alınamadı." }
        } else message = "Konum izni verilmedi."
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchCurrentLocation(context) { point, error -> if (point != null) applyPickup(point) else message = error ?: "Konum alınamadı." }
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        restoreActiveRequest(phone).onSuccess { item ->
            if (item != null) {
                activeRequestId = item.optString("id")
                requestStatus = item.optString("status", "SEARCHING")
                pickup = item.optString("pickup_address", pickup)
                destination = item.optString("destination_address", destination)
                val pLat = item.optDouble("pickup_lat", Double.NaN); val pLng = item.optDouble("pickup_lng", Double.NaN)
                val dLat = item.optDouble("destination_lat", Double.NaN); val dLng = item.optDouble("destination_lng", Double.NaN)
                if (!pLat.isNaN() && !pLng.isNaN()) pickupPoint = LatLng(pLat, pLng)
                if (!dLat.isNaN() && !dLng.isNaN()) destinationPoint = LatLng(dLat, dLng)
                activeRequestId?.let { refreshRequestDetails(it) }
            }
        }
    }

    LaunchedEffect(pickupPoint, destinationPoint) {
        val from = pickupPoint; val to = destinationPoint
        if (from != null && to != null) {
            routeLoading = true
            fetchRoute(from, to).onSuccess { routeInfo = it }.onFailure { message = "Rota alınamadı: ${it.message}" }
            routeLoading = false
        }
    }

    val distanceKm = routeInfo?.distanceKm ?: 0.0
    val price = if (distanceKm > 0) (250 + distanceKm * 30).roundToInt() else 0
    val canCall = selected != null && pickupPoint != null && destinationPoint != null && routeInfo != null && !sending
    val assigned = requestStatus != "SEARCHING" && requestStatus != "IDLE"

    Box(Modifier.fillMaxSize().background(Background)) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
            pickupPoint?.let { Marker(state = rememberMarkerState(position = it), title = "Alım noktası") }
            destinationPoint?.let { Marker(state = rememberMarkerState(position = it), title = "Varış noktası") }
            driverPoint?.let { Marker(state = rememberMarkerState(position = it), title = "Valeniz") }
            routeInfo?.points?.takeIf { it.size > 1 }?.let { Polyline(points = it, color = Orange, width = 12f) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(54.dp).background(Color(0xEE101419), CircleShape), contentAlignment = Alignment.Center) {
                Text("V", color = Orange, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.background(Color(0xEE14191F), RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(if (socketConnected) Success else Orange, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(if (trackingActive) "Canlı takip" else "Hazır", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!trackingActive) {
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF514191F)),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(Modifier.padding(18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(74.dp).height(5.dp).background(Border, CircleShape))
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Orange, CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(pickup, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                            Row(Modifier.fillMaxWidth().clickable { showDestinationSearch = true }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("■", color = Color(0xFFFF5364), fontSize = 12.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(if (destinationPoint == null) "Nereye gidilecek?" else destination, color = if (destinationPoint == null) Muted else Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                Text("＋", color = Muted, fontSize = 28.sp)
                            }
                        }
                    }

                    AnimatedVisibility(showDestinationSearch) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(destinationQuery, { destinationQuery = it }, Modifier.fillMaxWidth(), placeholder = { Text("Adres veya yer adı") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
                            Button(onClick = {
                                if (destinationQuery.length < 3) return@Button
                                searchingPlaces = true
                                scope.launch {
                                    searchPlaces(destinationQuery, pickupPoint).onSuccess { suggestions = it }.onFailure { message = it.message ?: "Adres bulunamadı" }
                                    searchingPlaces = false
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                                if (searchingPlaces) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Adresi ara", fontWeight = FontWeight.Bold)
                            }
                            suggestions.take(4).forEach { place ->
                                Row(Modifier.fillMaxWidth().background(SurfaceSoft, RoundedCornerShape(14.dp)).clickable {
                                    destinationPoint = place.point; destination = place.displayName; destinationQuery = place.displayName; suggestions = emptyList(); showDestinationSearch = false
                                }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("📍"); Spacer(Modifier.width(10.dp)); Text(place.displayName, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Text("Sık kullanılanlar", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickPlaceCard("⌂", "Ev", Modifier.weight(1f))
                        QuickPlaceCard("▣", "İş", Modifier.weight(1f))
                    }

                    if (selected != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable(onClick = onOpenVehicles)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).background(Color(0xFF2C2519), CircleShape), contentAlignment = Alignment.Center) { Text("🚘") }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${selected.brand} ${selected.model}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(selected.plate, color = Muted, fontSize = 12.sp)
                                }
                                Text("Değiştir", color = Orange, fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val p = pickupPoint ?: return@Button
                            val d = destinationPoint ?: return@Button
                            val vehicle = selected ?: return@Button
                            sending = true
                            scope.launch {
                                createRealValetRequest(phone, vehicle, pickup, p, destination, d, distanceKm, price.toDouble()) { message = it }
                                    .onSuccess { id -> activeRequestId = id; requestStatus = "SEARCHING"; message = "Size en yakın uygun valeler aranıyor."; socket.emit("request:join", JSONObject().put("requestId", id)) }
                                    .onFailure { message = "Talep oluşturulamadı: ${it.message}" }
                                sending = false
                            }
                        },
                        enabled = canCall,
                        modifier = Modifier.fillMaxWidth().height(66.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, disabledContainerColor = Color(0xFF5A4A31)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (sending) "Talep gönderiliyor..." else "Vale çağır", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            if (price > 0) Text("Tahmini ücret ₺$price • ${"%.1f".format(distanceKm)} km", color = Color.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            TrackingBottomSheet(
                status = requestStatus,
                assigned = assigned,
                driverInfo = driverInfo,
                pickup = pickup,
                destination = destination,
                price = price,
                distanceKm = distanceKm,
                routeMinutes = routeInfo?.durationMinutes ?: 0,
                onCall = {
                    val number = driverInfo?.phone.orEmpty()
                    if (number.isNotBlank()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                },
                onMessage = {
                    val number = driverInfo?.phone.orEmpty()
                    if (number.isNotBlank()) context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
                },
                onCancel = {
                    val id = activeRequestId
                    if (id != null) {
                        scope.launch {
                            cancelCustomerRequest(phone, id).onSuccess { requestStatus = "CANCELLED"; activeRequestId = null; driverInfo = null; driverPoint = null }
                                .onFailure { message = it.message ?: "Talep iptal edilemedi" }
                        }
                    }
                }
            )
        }

        if (routeLoading) Box(Modifier.fillMaxSize().background(Color(0x44000000)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) }
    }
}

@Composable
private fun QuickPlaceCard(icon: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier.background(SurfaceSoft, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Color(0xFF2C2519), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(icon, color = Orange, fontSize = 20.sp) }
        Spacer(Modifier.width(10.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TrackingBottomSheet(
    status: String,
    assigned: Boolean,
    driverInfo: DriverInfo?,
    pickup: String,
    destination: String,
    price: Int,
    distanceKm: Double,
    routeMinutes: Int,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onCancel: () -> Unit
) {
    val pulse by animateFloatAsState(if (assigned) 1f else 0.86f, label = "searchPulse")
    var expanded by remember { mutableStateOf(true) }
    var dragDistance by remember { mutableStateOf(0f) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val panelHeight by animateDpAsState(
        targetValue = if (expanded) screenHeight * 0.72f else 190.dp,
        label = "trackingPanelHeight"
    )

    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(panelHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF714191F)),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(expanded) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> dragDistance += amount },
                            onDragEnd = {
                                if (dragDistance > 35f) expanded = false
                                if (dragDistance < -35f) expanded = true
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f }
                        )
                    }
                    .clickable { expanded = !expanded }
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(74.dp).height(5.dp).background(Border, CircleShape))
                Text(
                    if (expanded) "Aşağı kaydırarak haritayı büyüt" else "Yukarı kaydırarak ayrıntıları aç",
                    color = Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            if (!expanded) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).background(Color(0xFF2D210C), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (assigned) "👤" else "V", color = Orange, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (assigned) requestStatusText(status) else "Valeniz aranıyor",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (assigned) (driverInfo?.name ?: "Atanan vale") else "Yakındaki uygun valeler aranıyor",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    if (assigned) {
                        Text(
                            if (routeMinutes > 0) "$routeMinutes dk" else "Canlı",
                            color = Orange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(if (assigned) requestStatusText(status) else "Valeniz aranıyor", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(if (assigned) "Valeniz konumunuza doğru geliyor." else "Size en yakın uygun valeler aranıyor. Lütfen bekleyin.", color = Muted)

                    if (!assigned) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.scale(pulse).size(88.dp).background(Color(0x332D210C), CircleShape), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(62.dp).background(Color(0xFF2D210C), CircleShape), contentAlignment = Alignment.Center) { Text("V", color = Orange, fontSize = 34.sp, fontWeight = FontWeight.Black) }
                            }
                            Spacer(Modifier.height(8.dp)); Text("Yakındaki valeler aranıyor", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Konum canlı güncelleniyor", color = Muted, fontSize = 13.sp)
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp).background(Color(0xFF2C2519), CircleShape), contentAlignment = Alignment.Center) { Text("👤", fontSize = 30.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(driverInfo?.name ?: "Atanan vale", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("★ ${"%.1f".format(driverInfo?.rating ?: 5.0)}  •  ${driverInfo?.plate.orEmpty()}", color = Muted, fontSize = 13.sp)
                                }
                                CircleAction("☎", "Ara", Orange, onCall)
                                Spacer(Modifier.width(9.dp))
                                CircleAction("•••", "Mesaj", SurfaceDark, onMessage)
                            }
                        }
                        StatusProgress(status)
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("●  $pickup", color = Color.White, fontSize = 13.sp)
                            Box(Modifier.padding(start = 5.dp).width(1.dp).height(18.dp).background(Border))
                            Text("■  $destination", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Tahmini ücret", if (price > 0) "₺$price" else "—")
                        Metric("Rota", if (distanceKm > 0) "${"%.1f".format(distanceKm)} km" else "—")
                        Metric("Tahmini varış", if (routeMinutes > 0) "$routeMinutes dk" else "—")
                    }
                    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A2227)), shape = RoundedCornerShape(18.dp)) {
                        Text("Vale talebini iptal et", color = Danger, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}


@Composable
private fun CircleAction(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(color, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontWeight = FontWeight.Bold) }
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Muted, fontSize = 11.sp); Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable
private fun StatusProgress(status: String) {
    val steps = listOf("DRIVER_EN_ROUTE", "ARRIVED", "VEHICLE_RECEIVED", "IN_TRANSIT")
    val index = when (status) {
        "ASSIGNED", "DRIVER_EN_ROUTE" -> 0
        "ARRIVED" -> 1
        "VEHICLE_RECEIVED" -> 2
        "IN_TRANSIT", "DELIVERED" -> 3
        else -> 0
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Yola çıktı", "Yaklaşıyor", "Konumda", "Aracınızla").forEachIndexed { i, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(30.dp).background(if (i <= index) Orange else SurfaceSoft, CircleShape), contentAlignment = Alignment.Center) { Text(if (i <= index) "✓" else "•", color = if (i <= index) Color.Black else Muted) }
                Text(label, color = if (i <= index) Orange else Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun VehiclesScreen(vehicles: List<Vehicle>, selectedVehicleId: Int?, onSelect: (Int) -> Unit, onDelete: (Int) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp)); TopBar("Araçlarım", onBack)
        Text("Vale çağırırken kullanacağınız aracı seçin veya yeni araç ekleyin.", color = Muted, lineHeight = 20.sp)
        if (vehicles.isEmpty()) EmptyVehicleCard(onAdd) else vehicles.forEach { v -> VehicleCard(v, v.id == selectedVehicleId, { onSelect(v.id) }, { onDelete(v.id) }) }
        Spacer(Modifier.height(4.dp)); PrimaryButton("+ Yeni araç ekle", true, onAdd); Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AddVehicleScreen(onBack: () -> Unit, onSave: (Vehicle) -> Unit) {
    BackHandler(onBack = onBack)
    var plate by remember { mutableStateOf("") }; var brand by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }; var year by remember { mutableStateOf("") }; var transmission by remember { mutableStateOf("Otomatik") }
    val valid = plate.length >= 5 && brand.isNotBlank() && model.isNotBlank() && color.isNotBlank() && year.length == 4

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp)); TopBar("Araç ekle", onBack)
        Text("Vale çağırırken kullanılacak temel araç bilgilerini girin.", color = Muted, lineHeight = 20.sp)
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Araç bilgileri", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(plate, { plate = normalizePlate(it) }, Modifier.fillMaxWidth(), label = { Text("Plaka") }, placeholder = { Text("34 ABC 123") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(brand, { brand = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("Marka") }, placeholder = { Text("BMW") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(model, { model = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Model") }, placeholder = { Text("520i") }, singleLine = true, colors = fieldColors())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(color, { color = it.take(18) }, Modifier.weight(1f), label = { Text("Renk") }, singleLine = true, colors = fieldColors())
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Model yılı") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors())
                }
                Text("Vites tipi", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip("Otomatik", transmission == "Otomatik") { transmission = "Otomatik" }
                    ChoiceChip("Manuel", transmission == "Manuel") { transmission = "Manuel" }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF12291D)), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Success, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.size(10.dp)); Text("Araç fotoğrafı ve ruhsat yüklemesi gerekmiyor.", color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        PrimaryButton("Aracı kaydet", valid) { onSave(Vehicle(0, plate, brand.trim(), model.trim(), color.trim(), year, transmission)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VehicleCard(vehicle: Vehicle, selected: Boolean, onSelect: () -> Unit, secondaryAction: () -> Unit, compact: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF282218) else SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(SurfaceSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("🚗", fontSize = 25.sp) }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${vehicle.brand} ${vehicle.model}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(vehicle.plate, color = Orange, fontWeight = FontWeight.Bold)
                    Text("${vehicle.color} • ${vehicle.year} • ${vehicle.transmission}", color = Muted, fontSize = 12.sp)
                }
                Text(if (selected) "✓ Seçili" else "Seç", color = if (selected) Success else Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (!compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (vehicle.verified) "✓ Doğrulandı" else "⏳ Doğrulama bekliyor", color = if (vehicle.verified) Success else Orange, fontSize = 12.sp)
                    Text("Sil", color = Danger, fontSize = 12.sp, modifier = Modifier.clickable(onClick = secondaryAction))
                }
            } else {
                Text("Araçlarımı yönet ›", color = Orange, fontSize = 12.sp, modifier = Modifier.clickable(onClick = secondaryAction))
            }
        }
    }
}

@Composable
private fun EmptyVehicleCard(onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🚘", fontSize = 38.sp); Spacer(Modifier.height(8.dp)); Text("Henüz araç eklenmedi", color = Color.White, fontWeight = FontWeight.Bold); Text("Vale çağırabilmek için aracınızı ekleyin.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(8.dp)); Text("+ Araç ekle", color = Orange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (selected) Orange else SurfaceSoft, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp)) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(SurfaceDark, CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", color = Orange, fontSize = 30.sp) }
        Spacer(Modifier.size(14.dp)); Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(Orange, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("V", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.size(12.dp)); Column { Text("ValeKapımda", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Güvenli • Hızlı • Profesyonel", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun SecurityCard() {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color(0xFF163525), CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = Success, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.size(12.dp)); Column { Text("Bilgileriniz güvende", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Telefon numaranız yalnızca hesap doğrulaması için kullanılır.", color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    PremiumPrimaryButton(text = text, enabled = enabled, onClick = onClick)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedLabelColor = Orange, unfocusedLabelColor = Muted, cursorColor = Orange, focusedTextColor = Color.White, unfocusedTextColor = Color.White)


@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: Context,
    callback: (LatLng?, String?) -> Unit
) {
    val client = LocationServices.getFusedLocationProviderClient(context)

    client.lastLocation
        .addOnSuccessListener { last ->

            if (last != null) {
                callback(
                    LatLng(last.latitude, last.longitude),
                    null
                )
            } else {

                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                )
                    .addOnSuccessListener { current ->

                        if (current != null) {
                            callback(
                                LatLng(current.latitude, current.longitude),
                                null
                            )
                        } else {
                            callback(
                                null,
                                "Konum alınamadı. GPS açık mı kontrol edin."
                            )
                        }
                    }
                    .addOnFailureListener {
                        callback(
                            null,
                            it.message ?: "Konum servisi hatası"
                        )
                    }
            }
        }
        .addOnFailureListener {
            callback(
                null,
                it.message ?: "Konum servisi hatası"
            )
        }
}

private fun directDistanceKm(a: LatLng, b: LatLng): Double {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0] / 1000.0
}

private fun coordinateLabel(prefix: String, point: LatLng) =
    "$prefix: %.5f, %.5f".format(point.latitude, point.longitude)


private suspend fun searchPlaces(query: String, near: LatLng?): Result<List<PlaceSuggestion>> = withContext(Dispatchers.IO) {
    runCatching {
        val nearQuery = if (near != null) "&lat=${near.latitude}&lng=${near.longitude}" else ""
        val array = getJsonArray("$API_BASE_URL/places/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}$nearQuery", null)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PlaceSuggestion(o.getString("displayName"), LatLng(o.getDouble("lat"), o.getDouble("lng")))
        }
    }
}

private suspend fun reverseGeocode(point: LatLng): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val o = getJsonObject("$API_BASE_URL/places/reverse?lat=${point.latitude}&lng=${point.longitude}", null)
        o.optString("displayName", coordinateLabel("Mevcut konum", point))
    }
}

private suspend fun fetchRoute(from: LatLng, to: LatLng): Result<RouteInfo> = withContext(Dispatchers.IO) {
    runCatching {
        val o = getJsonObject("$API_BASE_URL/route?fromLat=${from.latitude}&fromLng=${from.longitude}&toLat=${to.latitude}&toLng=${to.longitude}", null)
        val pts = o.getJSONArray("points")
        val points = (0 until pts.length()).map { i ->
            val p = pts.getJSONArray(i); LatLng(p.getDouble(0), p.getDouble(1))
        }
        RouteInfo(o.getDouble("distanceKm"), o.getInt("durationMinutes"), points)
    }
}

private const val API_BASE_URL = "https://valekapimda-api.onrender.com"
private fun saveAuthToken(context: Context, token: String) {
    context.getSharedPreferences("vale_auth", Context.MODE_PRIVATE)
        .edit()
        .putString("token", token)
        .apply()
}

private fun getAuthToken(context: Context): String? {
    return context.getSharedPreferences("vale_auth", Context.MODE_PRIVATE)
        .getString("token", null)
}

private suspend fun createRealValetRequest(
    phone: String,
    vehicle: Vehicle,
    pickupAddress: String,
    pickup: LatLng,
    destinationAddress: String,
    destination: LatLng,
    distanceKm: Double,
    quotedPrice: Double,
    onStageChanged: suspend (String) -> Unit
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        onStageChanged("Giriş doğrulanıyor...")

        val normalizedPhone = phone.filter(Char::isDigit).takeLast(10)
        if (normalizedPhone.length != 10) {
            throw IllegalStateException("Telefon numarası 10 haneli değil.")
        }

        val login = postJson(
            "$API_BASE_URL/auth/login",
            JSONObject().apply {
                put("role", "CUSTOMER")
                put("phone", "+90$normalizedPhone")
                put("fullName", "ValeKapımda Müşterisi")
            }
        )

        val token = login.optString("token")
        if (token.isBlank()) {
            throw IllegalStateException("Giriş cevabında token bulunamadı: $login")
        }

        onStageChanged("Araç bilgileri kaydediliyor...")

        val vehicleJson = postJson(
            "$API_BASE_URL/vehicles",
            JSONObject().apply {
                put("plate", vehicle.plate)
                put("brand", vehicle.brand)
                put("model", vehicle.model)
                put("color", vehicle.color)
                put("year", vehicle.year.toIntOrNull())
                put("transmission", vehicle.transmission)
            },
            token
        )

        val backendVehicleId = vehicleJson.optString("id")
        if (backendVehicleId.isBlank()) {
            throw IllegalStateException("Araç cevabında id bulunamadı: $vehicleJson")
        }

        onStageChanged("Vale talebi oluşturuluyor...")

        val request = postJson(
            "$API_BASE_URL/requests",
            JSONObject().apply {
                put("vehicleId", backendVehicleId)
                put("pickupAddress", pickupAddress)
                put("pickupLat", pickup.latitude)
                put("pickupLng", pickup.longitude)
                put("destinationAddress", destinationAddress)
                put("destinationLat", destination.latitude)
                put("destinationLng", destination.longitude)
                put("distanceKm", distanceKm)
                put("quotedPrice", quotedPrice)
            },
            token
        )

        val requestId = request.optString("id")
        if (requestId.isBlank()) {
            throw IllegalStateException("Talep cevabında id bulunamadı: $request")
        }

        requestId
    }.onFailure {
        Log.e("ValeKapimdaAPI", "Vale talebi oluşturulamadı", it)
    }
}

private suspend fun fetchCustomerHistory(phone: String): Result<List<HistoryItem>> = withContext(Dispatchers.IO) {
    runCatching {
        val login = customerLogin(phone)
        val token = login.optString("token")
        if (token.isBlank()) error("Oturum anahtarı alınamadı.")
        val array = getJsonArray("$API_BASE_URL/requests", token)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    HistoryItem(
                        id = item.optString("id"),
                        pickupAddress = item.optString("pickup_address", "Alım noktası"),
                        destinationAddress = item.optString("destination_address", "Varış noktası"),
                        distanceKm = item.optDouble("distance_km", 0.0),
                        quotedPrice = item.optDouble("quoted_price", 0.0),
                        status = item.optString("status", "SEARCHING"),
                        createdAt = item.optString("created_at")
                    )
                )
            }
        }
    }
}

private suspend fun fetchCustomerProfile(phone: String): Result<CustomerProfile> = withContext(Dispatchers.IO) {
    runCatching {
        val login = customerLogin(phone)
        val user = login.optJSONObject("user") ?: error("Kullanıcı bilgisi alınamadı.")
        CustomerProfile(
            fullName = user.optString("full_name", "ValeKapımda Müşterisi"),
            phone = user.optString("phone", "+90${phone.filter(Char::isDigit).takeLast(10)}")
        )
    }
}


private suspend fun restoreActiveRequest(phone: String): Result<JSONObject?> = withContext(Dispatchers.IO) {
    runCatching {
        val login = customerLogin(phone)
        val token = login.optString("token")
        val array = getJsonArray("$API_BASE_URL/requests", token)
        (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull {
            it.optString("status") !in listOf("COMPLETED", "CANCELLED")
        }
    }
}

private suspend fun fetchRequestDetails(phone: String, requestId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
    runCatching {
        val token = customerLogin(phone).optString("token")
        getJsonObject("$API_BASE_URL/requests/$requestId", token)
    }
}

private suspend fun cancelCustomerRequest(phone: String, requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val token = customerLogin(phone).optString("token")
        patchJson("$API_BASE_URL/requests/$requestId/cancel", JSONObject(), token)
        Unit
    }
}

private fun patchJson(url: String, body: JSONObject, token: String? = null): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "PATCH"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Sunucu hatası $code: $text")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    } finally { connection.disconnect() }
}

private fun customerLogin(phone: String): JSONObject {
    val normalizedPhone = phone.filter(Char::isDigit).takeLast(10)
    require(normalizedPhone.length == 10) { "Telefon numarası 10 haneli değil." }
    return postJson(
        "$API_BASE_URL/auth/login",
        JSONObject().apply {
            put("role", "CUSTOMER")
            put("phone", "+90$normalizedPhone")
            put("fullName", "ValeKapımda Müşterisi")
        }
    )
}

private fun getJsonArray(url: String, token: String? = null): JSONArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Sunucu hatası $code: $text")
        return JSONArray(text)
    } finally {
        connection.disconnect()
    }
}

private fun getJsonObject(url: String, token: String? = null): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Sunucu hatası $code: $text")
        return JSONObject(text)
    } finally {
        connection.disconnect()
    }
}

private fun formatServerDate(value: String): String {
    if (value.isBlank()) return ""
    return value.replace("T", " ").take(16)
}

private fun postJson(
    url: String,
    body: JSONObject,
    token: String? = null
): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false

        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Connection", "close")

        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }

        val requestBytes = body.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(requestBytes.size)

        Log.d("ValeKapimdaAPI", "POST $url body=$body")

        connection.outputStream.use { output ->
            output.write(requestBytes)
            output.flush()
        }

        val responseCode = connection.responseCode
        val responseText = (
                if (responseCode in 200..299) connection.inputStream
                else connection.errorStream
                )?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

        Log.d(
            "ValeKapimdaAPI",
            "POST $url response=$responseCode body=$responseText"
        )

        if (responseCode !in 200..299) {
            throw IllegalStateException(
                "Sunucu hatası $responseCode: ${responseText.ifBlank { "Boş hata cevabı" }}"
            )
        }

        if (responseText.isBlank()) {
            throw IllegalStateException("Sunucudan boş cevap geldi: $url")
        }

        return JSONObject(responseText)
    } catch (error: SocketTimeoutException) {
        throw IllegalStateException(
            "Sunucu 5 saniye içinde cevap vermedi. API terminalindeki hatayı kontrol edin.",
            error
        )
    } catch (error: ConnectException) {
        throw IllegalStateException(
            "Backend bağlantısı kurulamadı. Emülatör için adres $API_BASE_URL olmalı.",
            error
        )
    } catch (error: Exception) {
        if (error is IllegalStateException) throw error
        throw IllegalStateException(error.message ?: "Ağ bağlantısı hatası", error)
    } finally {
        connection.disconnect()
    }
}

private fun requestStatusText(status: String): String = when (status) {
    "SEARCHING" -> "Talebiniz oluşturuldu. Size uygun vale aranıyor..."
    "ASSIGNED" -> "Vale bulundu ve talebinizi kabul etti."
    "DRIVER_EN_ROUTE" -> "Valeniz alım noktasına doğru yola çıktı."
    "ARRIVED" -> "Valeniz alım noktasına ulaştı."
    "VEHICLE_RECEIVED" -> "Aracınız valeye teslim edildi."
    "IN_TRANSIT" -> "Aracınız varış noktasına doğru yolda."
    "DELIVERED" -> "Aracınız varış noktasına teslim edildi."
    "COMPLETED" -> "Vale işlemi başarıyla tamamlandı."
    "CANCELLED" -> "Vale talebi iptal edildi."
    else -> "Talebiniz güncellendi: $status"
}

private fun requestStatusLabel(status: String): String = when (status) {
    "SEARCHING" -> "Vale aranıyor"
    "ASSIGNED" -> "Vale bulundu"
    "DRIVER_EN_ROUTE" -> "Vale yolda"
    "ARRIVED" -> "Vale geldi"
    "VEHICLE_RECEIVED" -> "Araç teslim alındı"
    "IN_TRANSIT" -> "Yolculuk başladı"
    "DELIVERED" -> "Araç teslim edildi"
    "COMPLETED" -> "İşlem tamamlandı"
    "CANCELLED" -> "İptal edildi"
    else -> status
}

private fun requestButtonText(status: String): String = when (status) {
    "SEARCHING" -> "Vale aranıyor..."
    "ASSIGNED" -> "Vale bulundu ✓"
    "DRIVER_EN_ROUTE" -> "Vale yolda"
    "ARRIVED" -> "Vale geldi"
    "VEHICLE_RECEIVED" -> "Araç teslim alındı"
    "IN_TRANSIT" -> "Yolculuk başladı"
    "DELIVERED" -> "Araç teslim edildi"
    "COMPLETED" -> "İşlem tamamlandı ✓"
    "CANCELLED" -> "Talep iptal edildi"
    else -> "Talep oluşturuldu ✓"
}

private fun formatPhone(phone: String): String = if (phone.length != 10) phone else "${phone.take(3)} ${phone.substring(3, 6)} ${phone.substring(6, 8)} ${phone.takeLast(2)}"
private fun normalizePlate(value: String): String = value.uppercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").take(12)
