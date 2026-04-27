package com.example.feature.order.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feature.order.domain.model.OrderStatus

@Composable
fun OrderStatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        OrderStatus.PENDING -> Color(0xFFFFF9C4) to Color(0xFFF57F17)
        OrderStatus.PARTIALLY_MATCHED -> Color(0xFFFFE0B2) to Color(0xFFE65100)
        OrderStatus.FULLY_MATCHED -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
        OrderStatus.CANCELLED -> Color(0xFFF5F5F5) to Color(0xFF757575)
        OrderStatus.REJECTED -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    }
    Text(
        text = status.label,
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
