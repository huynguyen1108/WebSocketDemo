package com.example.feature.order.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import java.text.NumberFormat
import java.util.Locale

private val numFmt = NumberFormat.getNumberInstance(Locale("vi", "VN"))

@Composable
fun OrderListItem(
    order: Order,
    onCancel: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val sideColor = if (order.side == OrderSide.BUY) Color(0xFF1565C0) else Color(0xFFC62828)
    val sideLabel = if (order.side == OrderSide.BUY) "MUA" else "BÁN"

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$sideLabel  ${order.symbol}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = sideColor,
                    )
                    OrderStatusChip(order.status)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue("Loại", order.type.label)
                    LabelValue("Giá", if (order.price > 0) numFmt.format(order.price) else "---")
                    LabelValue("KL", "${numFmt.format(order.quantity)}")
                }
                if (order.matchedQuantity > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LabelValue("Đã khớp", numFmt.format(order.matchedQuantity))
                        LabelValue("Giá khớp", numFmt.format(order.matchedPrice))
                    }
                }
            }
            if (order.isActive && onCancel != null) {
                IconButton(onClick = { onCancel(order.orderId) }) {
                    Icon(Icons.Default.Cancel, "Huỷ lệnh", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
