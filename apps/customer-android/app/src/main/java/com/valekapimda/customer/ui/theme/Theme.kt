package com.valekapimda.customer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val VkOrange = Color(0xFFFF8A00)
val VkOrangeLight = Color(0xFFFFB347)
val VkBackground = Color(0xFF090D12)
val VkSurface = Color(0xFF141A22)
val VkSurfaceSoft = Color(0xFF1B232E)
val VkBorder = Color(0xFF2A3442)
val VkTextPrimary = Color(0xFFF7F9FC)
val VkTextSecondary = Color(0xFF98A2B3)
val VkSuccess = Color(0xFF45D483)
val VkDanger = Color(0xFFFF6B6B)

private val PremiumDarkColors = darkColorScheme(
    primary = VkOrange, onPrimary = Color.White,
    secondary = VkOrangeLight, background = VkBackground,
    onBackground = VkTextPrimary, surface = VkSurface,
    onSurface = VkTextPrimary, surfaceVariant = VkSurfaceSoft,
    onSurfaceVariant = VkTextSecondary, outline = VkBorder, error = VkDanger
)

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val PremiumTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
)

@Composable
fun ValeKapimdaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PremiumDarkColors, typography = PremiumTypography, shapes = PremiumShapes, content = content)
}
