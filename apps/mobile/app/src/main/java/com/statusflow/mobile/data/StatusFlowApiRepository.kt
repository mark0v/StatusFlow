package com.statusflow.mobile.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

data class MobileOrderSummary(
    val id: String,
    val code: String,
    val title: String,
    val customerName: String,
    val rawStatus: String,
    val statusLabel: String,
    val statusColor: Color,
    val updatedAtLabel: String
)

data class MobileUserSummary(
    val id: String,
    val email: String,
    val name: String,
    val role: String
)

data class MobileSessionSummary(
    val accessToken: String,
    val email: String,
    val name: String,
    val role: String
)

data class MobileDashboardData(
    val orders: List<MobileOrderSummary>,
    val users: List<MobileUserSummary>,
    val allowedTransitions: Map<String, List<String>>,
    val syncState: MobileSyncState = MobileSyncState()
)

data class MobileOrderComment(
    val id: String,
    val body: String,
    val authorName: String,
    val createdAtLabel: String
)

data class MobileOrderHistoryEvent(
    val id: String,
    val summary: String,
    val reason: String,
    val actorName: String,
    val changedAtLabel: String
)

data class MobileOrderDetail(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val customerName: String,
    val rawStatus: String,
    val statusLabel: String,
    val statusColor: Color,
    val updatedAtLabel: String,
    val comments: List<MobileOrderComment>,
    val history: List<MobileOrderHistoryEvent>
)

data class MobileMutationResult(
    val queuedOffline: Boolean
)

class StatusFlowApiRepository(
    private val apiService: OrderApiService = OrderApiClient.service,
    private val cacheStore: StatusFlowCacheStore
) {
    companion object {
        @Volatile
        private var instance: StatusFlowApiRepository? = null

        fun from(context: Context): StatusFlowApiRepository {
            return instance ?: synchronized(this) {
                instance ?: StatusFlowApiRepository(
                    cacheStore = StatusFlowCacheStore(
                        StatusFlowLocalDatabase.getInstance(context).cacheDao()
                    )
                ).also { instance = it }
            }
        }
    }

    fun getStoredSession(): MobileSessionSummary? {
        return MobileSessionStore.currentSession()?.let { session ->
            MobileSessionSummary(
                accessToken = session.accessToken,
                email = session.email,
                name = session.name,
                role = session.role
            )
        }
    }

    suspend fun login(email: String, password: String): MobileSessionSummary {
        val response = apiService.login(
            LoginRequest(
                email = email.trim().lowercase(),
                password = password
            )
        )
        val session = MobileSession(
            accessToken = response.access_token,
            email = response.user.email,
            name = response.user.name,
            role = response.user.role
        )
        MobileSessionStore.saveSession(session)
        return MobileSessionSummary(
            accessToken = session.accessToken,
            email = session.email,
            name = session.name,
            role = session.role
        )
    }

    fun clearSession() {
        MobileSessionStore.clearSession()
    }

    suspend fun fetchDashboardData(): MobileDashboardData {
        return runCatching {
            flushPendingMutations()
            val users = apiService.listUsers()
            val orders = apiService.listOrders()
            val lifecycle = apiService.getOrderStatusLifecycle()
            cacheStore.cacheRemoteDashboard(orders, users, lifecycle)
            MobileDashboardData(
                orders = orders.map(::mapOrder),
                users = users.map { user ->
                    MobileUserSummary(
                        id = user.id,
                        email = user.email,
                        name = user.name,
                        role = user.role
                    )
                },
                allowedTransitions = lifecycle.allowed_transitions,
                syncState = cacheStore.readSyncState(isUsingCachedData = false)
            )
        }.getOrElse { throwable ->
            cacheStore.updateSyncError(throwable.message)
            cacheStore.readDashboard()?.copy(
                syncState = cacheStore.readSyncState(isUsingCachedData = true)
            ) ?: throw throwable
        }
    }

    suspend fun createOrder(title: String, description: String, customerId: String): MobileMutationResult {
        try {
            val detail = apiService.createOrder(
                CreateOrderRequest(
                    title = title,
                    description = description,
                    customer_id = customerId
                )
            )
            cacheStore.cacheRemoteDetail(detail)
            return MobileMutationResult(queuedOffline = false)
        } catch (exception: IOException) {
            val customer = cachedUser(customerId) ?: MobileUserSummary(customerId, "", "Customer", "customer")
            val localId = "local-order-${UUID.randomUUID()}"
            val code = "LOCAL-${localId.takeLast(4).uppercase()}"
            cacheStore.enqueueCreateOrder(localId, code, title, description, customer)
            return MobileMutationResult(queuedOffline = true)
        }
    }

    suspend fun fetchOrderDetail(orderId: String): MobileOrderDetail {
        return runCatching {
            val detail = apiService.getOrder(orderId)
            cacheStore.cacheRemoteDetail(detail)
            mapOrderDetail(detail)
        }.getOrElse { throwable ->
            cacheStore.readOrderDetail(orderId) ?: throw throwable
        }
    }

    suspend fun transitionOrderStatus(orderId: String, changedById: String, toStatus: String): MobileMutationResult {
        try {
            val detail = apiService.transitionOrderStatus(
                orderId = orderId,
                payload = TransitionOrderStatusRequest(
                    changed_by_id = changedById,
                    to_status = toStatus,
                    reason = "Operator moved order to ${statusLabel(toStatus)}."
                )
            )
            cacheStore.cacheRemoteDetail(detail)
            return MobileMutationResult(queuedOffline = false)
        } catch (exception: IOException) {
            val actor = cachedUser(changedById) ?: MobileUserSummary(changedById, "", "Operator", "operator")
            cacheStore.enqueueStatusTransition(orderId, actor, toStatus)
            return MobileMutationResult(queuedOffline = true)
        }
    }

    suspend fun addComment(orderId: String, authorId: String, body: String): MobileMutationResult {
        try {
            val detail = apiService.addComment(
                orderId = orderId,
                payload = AddCommentRequest(
                    author_id = authorId,
                    body = body
                )
            )
            cacheStore.cacheRemoteDetail(detail)
            return MobileMutationResult(queuedOffline = false)
        } catch (exception: IOException) {
            val author = cachedUser(authorId) ?: MobileUserSummary(authorId, "", "Operator", "operator")
            cacheStore.enqueueComment(orderId, author, body)
            return MobileMutationResult(queuedOffline = true)
        }
    }

    private suspend fun flushPendingMutations() {
        val pending = cacheStore.listPendingMutations()
        pending.forEach { mutation ->
            try {
                when (mutation.type) {
                    "create_order" -> {
                        val detail = apiService.createOrder(
                            CreateOrderRequest(
                                title = mutation.title.orEmpty(),
                                description = mutation.description.orEmpty(),
                                customer_id = mutation.userId.orEmpty()
                            )
                        )
                        mutation.localOrderId?.let { cacheStore.replaceLocalOrder(it, detail) }
                    }
                    "add_comment" -> {
                        val orderId = mutation.orderId ?: return@forEach
                        val detail = apiService.addComment(
                            orderId = orderId,
                            payload = AddCommentRequest(
                                author_id = mutation.userId.orEmpty(),
                                body = mutation.body.orEmpty()
                            )
                        )
                        cacheStore.cacheRemoteDetail(detail)
                    }
                    "transition_status" -> {
                        val orderId = mutation.orderId ?: return@forEach
                        val detail = apiService.transitionOrderStatus(
                            orderId = orderId,
                            payload = TransitionOrderStatusRequest(
                                changed_by_id = mutation.userId.orEmpty(),
                                to_status = mutation.toStatus.orEmpty(),
                                reason = "Operator moved order to ${statusLabel(mutation.toStatus.orEmpty())}."
                            )
                        )
                        cacheStore.cacheRemoteDetail(detail)
                    }
                }
                cacheStore.deletePendingMutation(mutation.id)
            } catch (_: IOException) {
                return
            }
        }
    }

    private suspend fun cachedUser(userId: String): MobileUserSummary? {
        return cacheStore.readDashboard()?.users?.firstOrNull { it.id == userId }
    }

    private fun mapOrder(response: OrderApiResponse): MobileOrderSummary {
        return MobileOrderSummary(
            id = response.id,
            code = response.code,
            title = response.title,
            customerName = response.customer_name,
            rawStatus = response.status,
            statusLabel = statusLabel(response.status),
            statusColor = statusColor(response.status),
            updatedAtLabel = formatTimestamp(response.updated_at)
        )
    }

    private fun mapOrderDetail(response: OrderDetailApiResponse): MobileOrderDetail {
        return MobileOrderDetail(
            id = response.id,
            code = response.code,
            title = response.title,
            description = response.description.ifBlank { "No description provided yet." },
            customerName = response.customer_name,
            rawStatus = response.status,
            statusLabel = statusLabel(response.status),
            statusColor = statusColor(response.status),
            updatedAtLabel = formatTimestamp(response.updated_at),
            comments = response.comments.map { comment ->
                MobileOrderComment(
                    id = comment.id,
                    body = comment.body,
                    authorName = comment.author.name,
                    createdAtLabel = formatTimestamp(comment.created_at)
                )
            },
            history = response.history.map { event ->
                MobileOrderHistoryEvent(
                    id = event.id,
                    summary = buildHistorySummary(event.from_status, event.to_status),
                    reason = event.reason.ifBlank { "No reason provided." },
                    actorName = event.changed_by.name,
                    changedAtLabel = formatTimestamp(event.changed_at)
                )
            }
        )
    }

    private fun buildHistorySummary(fromStatus: String?, toStatus: String): String {
        return if (fromStatus == null) {
            "Created in ${statusLabel(toStatus)}"
        } else {
            "${statusLabel(fromStatus)} -> ${statusLabel(toStatus)}"
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
