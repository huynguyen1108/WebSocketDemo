package com.example.feature.stock.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val ColorUp = Color(0xFF00C853)
val ColorDown = Color(0xFFFF1744)
val ColorFlat = Color(0xFFFFAB00)
val ColorUpBg = Color(0x2200C853)
val ColorDownBg = Color(0x22FF1744)

@Composable
fun PriceText(
    price: Double,
    change: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Flash background khi giá thay đổi
    var flashColor by remember { mutableStateOf(Color.Transparent) }
    val animBg by animateColorAsState(
        targetValue = flashColor,
        animationSpec = tween(400),
        label = "flash",
    )

    LaunchedEffect(price) {
        flashColor = when {
            change > 0 -> ColorUpBg
            change < 0 -> ColorDownBg
            else -> Color.Transparent
        }
        delay(350)
        flashColor = Color.Transparent
    }

    val textColor = when {
        change > 0 -> ColorUp
        change < 0 -> ColorDown
        else -> ColorFlat
    }

    Text(
        text = formatPrice(price),
        style = style,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(animBg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun ChangeText(change: Double, changePercent: Double, style: TextStyle, modifier: Modifier = Modifier) {
    val color = when {
        change > 0 -> ColorUp
        change < 0 -> ColorDown
        else -> ColorFlat
    }
    val sign = if (change >= 0) "+" else ""
    Text(
        text = "$sign${formatPrice(change)} ($sign${"%.2f".format(changePercent)}%)",
        style = style,
        color = color,
        modifier = modifier,
    )
}

fun formatPrice(price: Double): String = "%,.0f".format(price)

fun formatVolume(vol: Long): String = when {
    vol >= 1_000_000 -> "${"%.1f".format(vol / 1_000_000.0)}M"
    vol >= 1_000 -> "${"%.0f".format(vol / 1_000.0)}K"
    else -> vol.toString()
}
