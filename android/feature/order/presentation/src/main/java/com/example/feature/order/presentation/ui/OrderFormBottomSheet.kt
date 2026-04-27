package com.example.feature.order.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import com.example.feature.order.presentation.OrderFormState
import com.example.feature.order.presentation.OrderViewModel

private val ColorBuy = Color(0xFF1565C0)
private val ColorSell = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormBottomSheet(
    viewModel: OrderViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        OrderFormContent(
            form = form,
            isSubmitting = uiState.isSubmitting,
            onSymbolChange = viewModel::onSymbolChange,
            onSideChange = viewModel::onSideChange,
            onTypeChange = viewModel::onTypeChange,
            onPriceChange = viewModel::onPriceChange,
            onQuantityChange = viewModel::onQuantityChange,
            onSubmit = {
                viewModel.submitOrder()
                onDismiss()
            },
        )
    }
}

@Composable
private fun OrderFormContent(
    form: OrderFormState,
    isSubmitting: Boolean,
    onSymbolChange: (String) -> Unit,
    onSideChange: (OrderSide) -> Unit,
    onTypeChange: (OrderType) -> Unit,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Đặt lệnh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Symbol
        OutlinedTextField(
            value = form.symbol,
            onValueChange = onSymbolChange,
            label = { Text("Mã cổ phiếu") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Side toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(OrderSide.BUY to "MUA", OrderSide.SELL to "BÁN").forEach { (side, label) ->
                Button(
                    onClick = { onSideChange(side) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (form.side == side) {
                            if (side == OrderSide.BUY) ColorBuy else ColorSell
                        } else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (form.side == side) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(label, fontWeight = FontWeight.Bold) }
            }
        }

        // Order type chips
        Text("Loại lệnh", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OrderType.entries.forEach { type ->
                FilterChip(
                    selected = form.type == type,
                    onClick = { onTypeChange(type) },
                    label = { Text(type.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }

        // Price
        OutlinedTextField(
            value = form.priceText,
            onValueChange = onPriceChange,
            label = { Text(if (form.isPriceEnabled) "Giá (VNĐ)" else "Giá (tự động)") },
            enabled = form.isPriceEnabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Quantity
        OutlinedTextField(
            value = form.quantityText,
            onValueChange = onQuantityChange,
            label = { Text("Khối lượng (bội số 100)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Submit
        Button(
            onClick = onSubmit,
            enabled = !isSubmitting && form.symbol.isNotBlank() && (form.quantityText.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (form.side == OrderSide.BUY) ColorBuy else ColorSell,
            ),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                val label = if (form.side == OrderSide.BUY) "ĐẶT MUA" else "ĐẶT BÁN"
                Text("$label ${form.symbol}", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
