package com.valekapimda.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
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
    var selectedVehicleId by remember { mutableStateOf(1) }

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(primary = Orange)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            when (screen) {
                Screen.Splash -> SplashScreen { screen = Screen.Login }
                Screen.Login -> LoginScreen(phone, { phone = it.filter(Char::isDigit).take(10) }) { screen = Screen.Otp }
                Screen.Otp -> OtpScreen(phone, { screen = Screen.Login }) { screen = Screen.Home }
                Screen.Home -> HomeScreen(
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
                        if (selectedVehicleId == id) selectedVehicleId = vehicles.firstOrNull()?.id ?: -1
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
private fun HomeScreen(
    vehicles: List<Vehicle>,
    selectedVehicleId: Int,
    onSelectVehicle: (Int) -> Unit,
    onOpenVehicles: () -> Unit,
    onLogout: () -> Unit,
    phone: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = vehicles.firstOrNull { it.id == selectedVehicleId }

    var pickupPoint by remember { mutableStateOf<LatLng?>(null) }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }
    var pickup by remember { mutableStateOf("Haritada alım noktasını seçin") }
    var destination by remember { mutableStateOf("Haritada gidilecek yeri seçin") }
    var message by remember { mutableStateOf("Önce konumunuzu alın, ardından haritada hedefe dokunun.") }
    var sending by remember { mutableStateOf(false) }
    var requestCreated by remember { mutableStateOf(false) }
    var activeRequestId by remember { mutableStateOf<String?>(null) }
    var requestStatus by remember { mutableStateOf("IDLE") }
    var socketConnected by remember { mutableStateOf(false) }

    val latestRequestId by rememberUpdatedState(activeRequestId)
    val socket = remember {
        IO.socket(
            API_BASE_URL,
            IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                reconnection = true
            }
        )
    }

    DisposableEffect(socket) {
        val connectListener = io.socket.emitter.Emitter.Listener {
            scope.launch { socketConnected = true }
        }
        val disconnectListener = io.socket.emitter.Emitter.Listener {
            scope.launch { socketConnected = false }
        }
        val connectErrorListener = io.socket.emitter.Emitter.Listener { args ->
            Log.e("ValeKapimdaSocket", "Bağlantı hatası: ${args.firstOrNull()}")
            scope.launch { socketConnected = false }
        }
        val requestUpdatedListener = io.socket.emitter.Emitter.Listener { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data != null) {
                val updatedId = data.optString("id")
                val updatedStatus = data.optString("status")

                if (updatedId.isNotBlank() && updatedId == latestRequestId) {
                    scope.launch {
                        requestStatus = updatedStatus
                        message = requestStatusText(updatedStatus)
                        if (updatedStatus == "COMPLETED" || updatedStatus == "CANCELLED") {
                            requestCreated = false
                        }
                    }
                }
            }
        }

        socket.on(Socket.EVENT_CONNECT, connectListener)
        socket.on(Socket.EVENT_DISCONNECT, disconnectListener)
        socket.on(Socket.EVENT_CONNECT_ERROR, connectErrorListener)
        socket.on("request:updated", requestUpdatedListener)
        socket.connect()

        onDispose {
            socket.off(Socket.EVENT_CONNECT, connectListener)
            socket.off(Socket.EVENT_DISCONNECT, disconnectListener)
            socket.off(Socket.EVENT_CONNECT_ERROR, connectErrorListener)
            socket.off("request:updated", requestUpdatedListener)
            socket.disconnect()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.0082, 28.9784), 10f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchCurrentLocation(context) { point, error ->
                if (point != null) {
                    pickupPoint = point
                    pickup = coordinateLabel("Mevcut konum", point)
                    message = "Alım noktası belirlendi. Şimdi haritada gidilecek yere dokunun."
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point, 15f)) }
                } else message = error ?: "Konum alınamadı."
            }
        } else message = "Konum izni verilmedi. Ayarlardan izin verebilir veya haritadan nokta seçebilirsiniz."
    }

    val distanceKm = remember(pickupPoint, destinationPoint) {
        if (pickupPoint != null && destinationPoint != null) {
            directDistanceKm(pickupPoint!!, destinationPoint!!)
        } else 0.0
    }
    val price = if (distanceKm > 0) (250 + distanceKm * 30).roundToInt() else 0
    val canCall = selected != null && pickupPoint != null && destinationPoint != null && !sending

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ValeKapımda", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Gerçek vale çağırma", color = Muted, fontSize = 13.sp)
            }
            Box(
                Modifier.size(42.dp).background(SurfaceDark, CircleShape).clickable(onClick = onLogout),
                contentAlignment = Alignment.Center
            ) { Text("Çık", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        Text("Araç seçimi", color = Color.White, fontWeight = FontWeight.SemiBold)
        if (selected == null) EmptyVehicleCard(onOpenVehicles)
        else VehicleCard(selected, true, { onSelectVehicle(selected.id) }, onOpenVehicles, compact = true)

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Canlı rota haritası", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Noktaya dokun", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().height(300.dp).background(SurfaceSoft, RoundedCornerShape(16.dp))) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { point ->
                            when {
                                pickupPoint == null -> {
                                    pickupPoint = point
                                    pickup = coordinateLabel("Alım", point)
                                    message = "Alım noktası seçildi. Şimdi gidilecek yere dokunun."
                                }
                                destinationPoint == null -> {
                                    destinationPoint = point
                                    destination = coordinateLabel("Varış", point)
                                    message = "Rota hazır. Bilgileri kontrol edip vale çağırabilirsiniz."
                                }
                                else -> {
                                    destinationPoint = point
                                    destination = coordinateLabel("Varış", point)
                                    requestCreated = false
                                    activeRequestId = null
                                    requestStatus = "IDLE"
                                    message = "Varış noktası güncellendi."
                                }
                            }
                        }
                    ) {
                        pickupPoint?.let { Marker(state = rememberMarkerState(position = it), title = "Alım noktası") }
                        destinationPoint?.let { Marker(state = rememberMarkerState(position = it), title = "Gidilecek yer") }
                    }
                }
                Button(
                    onClick = {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            fetchCurrentLocation(context) { point, error ->
                                if (point != null) {
                                    pickupPoint = point
                                    pickup = coordinateLabel("Mevcut konum", point)
                                    destinationPoint = null
                                    destination = "Haritada gidilecek yeri seçin"
                                    requestCreated = false
                                    activeRequestId = null
                                    requestStatus = "IDLE"
                                    message = "Konumunuz alındı. Şimdi haritada hedefe dokunun."
                                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point, 15f)) }
                                } else message = error ?: "Konum alınamadı."
                            }
                        } else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceSoft),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("📍 Gerçek Konumumu Kullan", color = Color.White, fontWeight = FontWeight.SemiBold) }

                Text("Noktaları sıfırla", color = Orange, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).clickable {
                        pickupPoint = null; destinationPoint = null
                        pickup = "Haritada alım noktasını seçin"
                        destination = "Haritada gidilecek yeri seçin"
                        requestCreated = false
                        activeRequestId = null
                        requestStatus = "IDLE"
                        message = "Alım ve varış noktalarını yeniden seçin."
                    })
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Yolculuk Bilgileri", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("📍 $pickup", color = Color.White, fontSize = 13.sp)
                Text("🏁 $destination", color = Color.White, fontSize = 13.sp)
                Text(message, color = if (requestCreated) Success else Muted, fontSize = 12.sp, lineHeight = 17.sp)
                if (activeRequestId != null) {
                    Text(
                        "Canlı bağlantı: ${if (socketConnected) "Aktif" else "Bağlanıyor..."}",
                        color = if (socketConnected) Success else Orange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Talep: ${activeRequestId!!.take(8).uppercase()} • ${requestStatusLabel(requestStatus)}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Yaklaşık mesafe", color = Muted)
                    Text(if (distanceKm > 0) "%.1f km".format(distanceKm) else "—", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Tahmini ücret", color = Muted)
                    Text(if (price > 0) "₺$price" else "—", color = Orange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        PrimaryButton(
            if (sending) "Talep gönderiliyor..."
            else if (requestCreated) requestButtonText(requestStatus)
            else "Vale Çağır",
            canCall && !requestCreated
        ) {
            val vehicle = selected ?: return@PrimaryButton
            val p = pickupPoint ?: return@PrimaryButton
            val d = destinationPoint ?: return@PrimaryButton
            sending = true
            message = "Sunucuya bağlanılıyor..."
            scope.launch {
                val result = createRealValetRequest(
                    phone = phone,
                    vehicle = vehicle,
                    pickupAddress = pickup,
                    pickup = p,
                    destinationAddress = destination,
                    destination = d,
                    distanceKm = distanceKm,
                    quotedPrice = price.toDouble(),
                    onStageChanged = { stage -> message = stage }
                )

                sending = false

                result.onSuccess { id ->
                    activeRequestId = id
                    requestStatus = "SEARCHING"
                    requestCreated = true
                    message = "Talebiniz oluşturuldu. Size uygun vale aranıyor..."
                }.onFailure { error ->
                    requestCreated = false
                    message = "Talep gönderilemedi: ${error.message ?: "Bilinmeyen hata"}"
                }
            }
        }
        Text("Android emülatörde backend adresi: http://10.0.2.2:4000", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun VehiclesScreen(vehicles: List<Vehicle>, selectedVehicleId: Int, onSelect: (Int) -> Unit, onDelete: (Int) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
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
    Button(onClick, Modifier.fillMaxWidth().height(58.dp), enabled = enabled, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White, disabledContainerColor = Color(0xFF5B4630), disabledContentColor = Color(0xFFB7A793))) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedLabelColor = Orange, unfocusedLabelColor = Muted, cursorColor = Orange, focusedTextColor = Color.White, unfocusedTextColor = Color.White)


@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(context: Context, callback: (LatLng?, String?) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) callback(LatLng(location.latitude, location.longitude), null)
            else callback(null, "Emülatörde konum seçilmemiş olabilir. Extended Controls > Location bölümünden konum gönderin.")
        }
        .addOnFailureListener { callback(null, it.message ?: "Konum servisi hatası") }
}

private fun directDistanceKm(a: LatLng, b: LatLng): Double {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0] / 1000.0
}

private fun coordinateLabel(prefix: String, point: LatLng) =
    "$prefix: %.5f, %.5f".format(point.latitude, point.longitude)

private const val API_BASE_URL = "http://10.0.2.2:4000"

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
            "$API_BASE_URL/auth/demo-login",
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
