package com.statusflow.mobile.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.IOException
import java.util.UUID
import retrofit2.HttpException

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

class MobileSessionExpiredException(cause: Throwable? = null) :
    IllegalStateException("Your session expired. Sign in again to continue.", cause)

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
            val users = authenticatedCall { apiService.listUsers() }
            val orders = authenticatedCall { apiService.listOrders() }
            val lifecycle = authenticatedCall { apiService.getOrderStatusLifecycle() }
            cacheStore.cacheRemoteDashboard(orders, users, lifecycle)
            MobileDashboardData(
                orders = orders.map(StatusFlowMobileMapper::mapOrderSummary),
                users = users.map(StatusFlowMobileMapper::mapUserSummary),
                allowedTransitions = lifecycle.allowed_transitions,
                syncState = cacheStore.readSyncState(isUsingCachedData = false)
            )
        }.getOrElse { throwable ->
            if (throwable is MobileSessionExpiredException) {
                throw throwable
            }
            cacheStore.updateSyncError(throwable.message)
            cacheStore.readDashboard()?.copy(
                syncState = cacheStore.readSyncState(isUsingCachedData = true)
            ) ?: throw throwable
        }
    }

    suspend fun createOrder(title: String, description: String, customerId: String): MobileMutationResult {
        try {
            val detail = authenticatedCall {
                apiService.createOrder(
                    CreateOrderRequest(
                        title = title,
                        description = description,
                        customer_id = customerId
                    )
                )
            }
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
            val detail = authenticatedCall { apiService.getOrder(orderId) }
            cacheStore.cacheRemoteDetail(detail)
            StatusFlowMobileMapper.mapOrderDetail(detail)
        }.getOrElse { throwable ->
            if (throwable is MobileSessionExpiredException) {
                throw throwable
            }
            cacheStore.readOrderDetail(orderId) ?: throw throwable
        }
    }

    suspend fun transitionOrderStatus(orderId: String, changedById: String, toStatus: String): MobileMutationResult {
        try {
            val detail = authenticatedCall {
                apiService.transitionOrderStatus(
                    orderId = orderId,
                    payload = TransitionOrderStatusRequest(
                        changed_by_id = changedById,
                        to_status = toStatus,
                        reason = "Operator moved order to ${StatusFlowMobileMapper.statusLabel(toStatus)}."
                    )
                )
            }
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
            val detail = authenticatedCall {
                apiService.addComment(
                    orderId = orderId,
                    payload = AddCommentRequest(
                        author_id = authorId,
                        body = body
                    )
                )
            }
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
                        val detail = authenticatedCall {
                            apiService.createOrder(
                                CreateOrderRequest(
                                    title = mutation.title.orEmpty(),
                                    description = mutation.description.orEmpty(),
                                    customer_id = mutation.userId.orEmpty()
                                )
                            )
                        }
                        mutation.localOrderId?.let { cacheStore.replaceLocalOrder(it, detail) }
                    }
                    "add_comment" -> {
                        val orderId = mutation.orderId ?: return@forEach
                        val detail = authenticatedCall {
                            apiService.addComment(
                                orderId = orderId,
                                payload = AddCommentRequest(
                                    author_id = mutation.userId.orEmpty(),
                                    body = mutation.body.orEmpty()
                                )
                            )
                        }
                        cacheStore.cacheRemoteDetail(detail)
                    }
                    "transition_status" -> {
                        val orderId = mutation.orderId ?: return@forEach
                        val detail = authenticatedCall {
                            apiService.transitionOrderStatus(
                                orderId = orderId,
                                payload = TransitionOrderStatusRequest(
                                    changed_by_id = mutation.userId.orEmpty(),
                                    to_status = mutation.toStatus.orEmpty(),
                                    reason = "Operator moved order to ${StatusFlowMobileMapper.statusLabel(mutation.toStatus.orEmpty())}."
                                )
                            )
                        }
                        cacheStore.cacheRemoteDetail(detail)
                    }
                }
                cacheStore.deletePendingMutation(mutation.id)
            } catch (exception: IOException) {
                cacheStore.updateSyncError("Offline sync paused until the connection returns.")
                return
            } catch (exception: HttpException) {
                when {
                    exception.code() in listOf(401, 403) -> throw MobileSessionExpiredException(exception)
                    exception.code() in listOf(400, 404, 409, 422) -> {
                        cacheStore.discardPendingMutation(mutation)
                        cacheStore.updateSyncError(buildMutationConflictMessage(mutation, exception.code()))
                    }
                    exception.code() >= 500 -> {
                        cacheStore.updateSyncError("Server sync is temporarily unavailable. Pending changes will retry.")
                        return
                    }
                    else -> {
                        cacheStore.updateSyncError("Sync paused after an unexpected ${exception.code()} response.")
                        return
                    }
                }
            }
        }
    }

    private suspend fun cachedUser(userId: String): MobileUserSummary? {
        return cacheStore.readDashboard()?.users?.firstOrNull { it.id == userId }
    }

    private suspend fun <T> authenticatedCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (exception: HttpException) {
            if (exception.code() in listOf(401, 403)) {
                throw MobileSessionExpiredException(exception)
            }
            throw exception
        }
    }

    private fun buildMutationConflictMessage(mutation: PendingMutationEntity, statusCode: Int): String {
        val subject = when (mutation.type) {
            "create_order" -> "A queued order draft"
            "add_comment" -> "A queued comment"
            "transition_status" -> "A queued status change"
            else -> "A queued change"
        }
        return "$subject was dropped after a permanent server response ($statusCode). Newer changes can continue syncing."
    }
}
