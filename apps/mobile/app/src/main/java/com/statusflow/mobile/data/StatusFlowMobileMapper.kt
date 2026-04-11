package com.statusflow.mobile.data

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object StatusFlowMobileMapper {
    fun mapUserSummary(user: UserApiResponse): MobileUserSummary {
        return MobileUserSummary(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role
        )
    }

    fun mapOrderSummary(order: OrderApiResponse): MobileOrderSummary {
        return MobileOrderSummary(
            id = order.id,
            code = order.code,
            title = order.title,
            customerName = order.customer_name,
            rawStatus = order.status,
            statusLabel = statusLabel(order.status),
            statusColor = statusColor(order.status),
            updatedAtLabel = formatTimestamp(order.updated_at)
        )
    }

    fun mapOrderDetail(order: OrderDetailApiResponse): MobileOrderDetail {
        return MobileOrderDetail(
            id = order.id,
            code = order.code,
            title = order.title,
            description = order.description.ifBlank { "No description provided yet." },
            customerName = order.customer_name,
            rawStatus = order.status,
            statusLabel = statusLabel(order.status),
            statusColor = statusColor(order.status),
            updatedAtLabel = formatTimestamp(order.updated_at),
            comments = order.comments.map(::mapComment),
            history = order.history.map(::mapHistory)
        )
    }

    fun mapCachedOrderSummary(order: CachedOrderEntity): MobileOrderSummary {
        return MobileOrderSummary(
            id = order.id,
            code = order.code,
            title = order.title,
            customerName = order.customerName,
            rawStatus = order.rawStatus,
            statusLabel = statusLabel(order.rawStatus),
            statusColor = statusColor(order.rawStatus),
            updatedAtLabel = formatTimestamp(order.updatedAt)
        )
    }

    fun mapCachedUserSummary(user: CachedUserEntity): MobileUserSummary {
        return MobileUserSummary(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role
        )
    }

    fun mapCachedOrderDetail(
        order: CachedOrderEntity,
        comments: List<CachedCommentEntity>,
        history: List<CachedHistoryEntity>
    ): MobileOrderDetail {
        return MobileOrderDetail(
            id = order.id,
            code = order.code,
            title = order.title,
            description = order.description.ifBlank { "No description provided yet." },
            customerName = order.customerName,
            rawStatus = order.rawStatus,
            statusLabel = statusLabel(order.rawStatus),
            statusColor = statusColor(order.rawStatus),
            updatedAtLabel = formatTimestamp(order.updatedAt),
            comments = comments.map(::mapCachedComment),
            history = history.map(::mapCachedHistory)
        )
    }

    fun mapRemoteComment(comment: OrderCommentApiResponse): MobileOrderComment = mapComment(comment)

    fun mapRemoteHistory(event: OrderHistoryApiResponse): MobileOrderHistoryEvent = mapHistory(event)

    fun mapCachedComment(comment: CachedCommentEntity): MobileOrderComment {
        return MobileOrderComment(
            id = comment.id,
            body = comment.body,
            authorName = comment.authorName,
            createdAtLabel = formatTimestamp(comment.createdAt)
        )
    }

    fun mapCachedHistory(event: CachedHistoryEntity): MobileOrderHistoryEvent {
        return MobileOrderHistoryEvent(
            id = event.id,
            summary = event.summary,
            reason = event.reason,
            actorName = event.actorName,
            changedAtLabel = formatTimestamp(event.changedAt)
        )
    }

    fun buildHistorySummary(fromStatus: String?, toStatus: String): String {
        return if (fromStatus == null) {
            "Created in ${statusLabel(toStatus)}"
        } else {
            "${statusLabel(fromStatus)} -> ${statusLabel(toStatus)}"
        }
    }

    fun statusLabel(status: String): String {
        return status.split("_").joinToString(" ") { token ->
            token.replaceFirstChar { character -> character.uppercase() }
        }
    }

    fun statusColor(status: String): Color {
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

    fun formatTimestamp(value: String): String {
        return runCatching {
            val instant = Instant.parse(value)
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }.getOrElse { value }
    }

    private fun mapComment(comment: OrderCommentApiResponse): MobileOrderComment {
        return MobileOrderComment(
            id = comment.id,
            body = comment.body,
            authorName = comment.author.name,
            createdAtLabel = formatTimestamp(comment.created_at)
        )
    }

    private fun mapHistory(event: OrderHistoryApiResponse): MobileOrderHistoryEvent {
        return MobileOrderHistoryEvent(
            id = event.id,
            summary = buildHistorySummary(event.from_status, event.to_status),
            reason = event.reason.ifBlank { "No reason provided." },
            actorName = event.changed_by.name,
            changedAtLabel = formatTimestamp(event.changed_at)
        )
    }
}
