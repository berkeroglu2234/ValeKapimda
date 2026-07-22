package com.valekapimda.customer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valekapimda.customer.ui.theme.VkBorder
import com.valekapimda.customer.ui.theme.VkOrange
import com.valekapimda.customer.ui.theme.VkSurface
import com.valekapimda.customer.ui.theme.VkTextSecondary

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.shadow(14.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = VkSurface.copy(alpha = 0.94f))
    ) { Box(Modifier.padding(18.dp)) { content() } }
}

@Composable
fun PremiumPrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp), clip = false),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VkOrange, contentColor = Color.White, disabledContainerColor = VkBorder, disabledContentColor = VkTextSecondary),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun PremiumLoading(text: String, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column { CircularProgressIndicator(color = VkOrange); Text(text, color = VkTextSecondary, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
fun AnimatedGlassCard(visible: Boolean = true, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })) { GlassCard(modifier, content) }
}
