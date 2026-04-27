package com.example.feature.stock.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feature.stock.domain.model.StockItem
import kotlinx.coroutines.delay

@Composable
fun StockListItem(
    stock: StockItem,
    modifier: Modifier = Modifier,
    onTradeClick: ((String) -> Unit)? = null,
) {
    // Flash toàn row khi giá thay đổi
    var rowFlash by remember { mutableStateOf(Color.Transparent) }
    val animRowBg by animateColorAsState(rowFlash, tween(500), label = "rowFlash")

    LaunchedEffect(stock.price) {
        rowFlash = when {
            stock.isUp -> Color(0x1100C853)
            stock.isDown -> Color(0x11FF1744)
            else -> Color.Transparent
        }
        delay(400)
        rowFlash = Color.Transparent
    }

    Surface(
        modifier = modifier
            .background(animRowBg)
            .then(if (onTradeClick != null) Modifier.clickable { onTradeClick(stock.symbol) } else Modifier),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Symbol + Exchange badge
                Column(modifier = Modifier.width(88.dp)) {
                    Text(
                        text = stock.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ExchangeBadge(stock.exchange)
                }

                // Company name
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                // Price + change column
                Column(horizontalAlignment = Alignment.End) {
                    PriceText(
                        price = stock.price,
                        change = stock.change,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ChangeText(
                        change = stock.change,
                        changePercent = stock.changePercent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "KL: ${formatVolume(stock.volume)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ExchangeBadge(exchange: String) {
    val key = exchange.uppercase()
    val bg = when (key) {
        "HNX" -> Color(0xFF1F2D4F)
        "UPCOM" -> Color(0xFF2D1F4F)
        else -> Color(0xFF1F3D2F)
    }
    val fg = when (key) {
        "HNX" -> Color(0xFF58A6FF)
        "UPCOM" -> Color(0xFFBC8CFF)
        else -> ColorUp
    }
    Text(
        text = exchange,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .size(width = 44.dp, height = 14.dp),
    )
}
