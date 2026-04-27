package com.example.feature.stock.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feature.stock.domain.model.MarketIndex

@Composable
fun MarketIndexCard(index: MarketIndex, modifier: Modifier = Modifier) {
    val targetColor = when {
        index.isUp -> ColorUp.copy(alpha = 0.15f)
        index.isDown -> ColorDown.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val animColor by animateColorAsState(targetColor, tween(600), label = "indexBg")

    Card(
        modifier = modifier.width(170.dp),
        colors = CardDefaults.cardColors(containerColor = animColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = index.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "%.2f".format(index.value),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    index.isUp -> ColorUp
                    index.isDown -> ColorDown
                    else -> ColorFlat
                },
            )
            ChangeText(
                change = index.change,
                changePercent = index.changePercent,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(6.dp))
            // Advances / Declines / No change
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MarketBreadthChip("↑${index.advances}", ColorUp)
                MarketBreadthChip("↓${index.declines}", ColorDown)
                MarketBreadthChip("─${index.noChanges}", ColorFlat)
            }
        }
    }
}

@Composable
private fun MarketBreadthChip(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}
