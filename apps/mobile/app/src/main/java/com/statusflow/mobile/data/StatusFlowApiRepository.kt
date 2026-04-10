package com.statusflow.mobile.data

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class MobileOrderSummary(
    val id: String,
    val code: String,
    val title: String,
    val customerName: String,
    val statusLabel: String,
    val statusColor: Color,
    val updatedAtLabel: String
)

class StatusFlowApiRepository(
    private val apiService: OrderApiService = OrderApiClient.service
) {
    suspend fun fetchOrders(): List<MobileOrderSummary> {
        return apiService.listOrders().map { response ->
            MobileOrderSummary(
                id = response.id,
                code = response.code,
                title = response.title,
                customerName = response.customer_name,
                statusLabel = statusLabel(response.status),
                statusColor = statusColor(response.status),
                updatedAtLabel = formatTimestamp(response.updated_at)
            )
        }
    }

    private fun statusLabel(status: String): String {
        return status.split("_").joinToString(" ") { token ->
            token.replaceFirstChar { character -> character.uppercase() }
        }
    }

    private fun statusColor(status: String): Color {
        return when (status) {
            "new" -> Color(0xFF92B1F7)
            "in_review" -> Color(0xFFFFD574)
            "approved" -> Color(0xFF8BE0C0)
            "rejected" -> Color(0xFFFF9D9D)
            "fulfilled" -> Color(0xFF9EFADB)
            "cancelled" -> Color(0xFFD2DAE6)
            else -> Color.White
        }
    }

    private fun formatTimestamp(value: String): String {
        return runCatching {
            val instant = Instant.parse(value)
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }.getOrElse { value }
    }
}
