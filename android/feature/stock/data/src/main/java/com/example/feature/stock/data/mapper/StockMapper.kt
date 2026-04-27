package com.example.feature.stock.data.mapper

import com.example.feature.stock.data.dto.MarketIndexDto
import com.example.feature.stock.data.dto.StockDto
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem

fun StockDto.toDomain(existing: StockItem? = null): StockItem = StockItem(
    symbol = symbol,
    name = name ?: existing?.name ?: symbol,
    exchange = exchange ?: existing?.exchange ?: "",
    price = price,
    reference = reference ?: existing?.reference ?: price,
    change = change,
    changePercent = changePercent,
    open = open ?: existing?.open ?: price,
    high = high ?: existing?.high ?: price,
    low = low ?: existing?.low ?: price,
    ceiling = ceiling ?: existing?.ceiling ?: price * 1.07,
    floor = floor ?: existing?.floor ?: price * 0.93,
    volume = volume,
    totalValue = totalValue ?: existing?.totalValue ?: 0.0,
)

fun MarketIndexDto.toDomain(): MarketIndex = MarketIndex(
    name = name,
    value = value,
    change = change,
    changePercent = changePercent,
    volume = volume,
    advances = advances,
    declines = declines,
    noChanges = noChanges,
)
