package com.valekapimda.driver

import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState


private val DriverBackground = Color(0xFF090D12)
private val DriverOrange = Color(0xFFFF9800)
private val DriverCard = Color(0xEE14191F)


@Composable
fun PremiumDriverHome(
    activeRequest: DriverRequest?,
    available: Boolean
) {
	val context = LocalContext.current	

    val request = activeRequest


    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(41.0082, 28.9784),
            12f
        )
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DriverBackground)
    ) {


        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        )
if (request != null) {

    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .fillMaxWidth(),

        colors = CardDefaults.cardColors(
            containerColor = Color(0xEE14191F)
        ),

        shape = RoundedCornerShape(30.dp)
    ) {

        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {


            Text(
                text = "🟠 Aktif Vale Görevi",
                color = DriverOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )


            Text(
                text = request.status,
                color = Color(0xFF55D98A),
                fontWeight = FontWeight.Bold
            )


            Spacer(
                modifier = Modifier.height(4.dp)
            )


            Text(
                text = "📍 Alış Noktası",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Text(
                text = request.pickupAddress,
                color = Color.White,
                fontSize = 15.sp
            )


            Text(
                text = "🎯 Teslim Noktası",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Text(
                text = request.destinationAddress,
                color = Color.White,
                fontSize = 15.sp
            )


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "💰 ₺${request.quotedPrice.toInt()}",
                    color = DriverOrange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
			Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
) {

    Button(
    onClick = {

        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:${request.customerPhone}")
        )

        context.startActivity(intent)

    },
    modifier = Modifier.weight(1f),
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF202832)
    )
) {
    Text(
        text = "📞 Ara",
        color = Color.White
    )
}


    Button(
        onClick = {},
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriverOrange
        )
    ) {
        Text(
            text = "💬 Mesaj",
            color = Color.Black
        )
    }

}
			
			
			Button(
    onClick = {

        val uri = Uri.parse(
            "google.navigation:q=${request.destinationAddress}"
        )

        val intent = Intent(
            Intent.ACTION_VIEW,
            uri
        )

        intent.setPackage("com.google.android.apps.maps")

        context.startActivity(intent)

    },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

                shape = RoundedCornerShape(26.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor = DriverOrange
                )
            ) {

                Text(
                    text = "🧭 Yol Tarifi Başlat",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }



            OutlinedButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),

                shape = RoundedCornerShape(24.dp)
            ) {

                Text(
                    text = "Görevi İptal Et",
                    color = Color.White
                )
            }

        }   // Column kapanışı
    }   // Aktif görev Card kapanışı


    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
                .padding(18.dp)
                .fillMaxWidth(),

            colors = CardDefaults.cardColors(
                containerColor = DriverCard
            ),

            shape = RoundedCornerShape(24.dp)
        ) {


            Row(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {


                Column {

                    Text(
                        text = "ValeKapımda Vale",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )


                    Text(
                        text = "Canlı görev ekranı",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }


                Text(
                    text = "● Online",
                    color = Color(0xFF55D98A),
                    fontWeight = FontWeight.Bold
                )

            }

        }

    }
}
}