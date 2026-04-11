package com.statusflow.mobile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Entity(tableName = "cached_orders")
data class CachedOrderEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val description: String,
    val customerName: String,
    val rawStatus: String,
    val updatedAt: String,
    val isPendingLocal: Boolean = false
)

@Entity(tableName = "cached_comments")
data class CachedCommentEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val body: String,
    val authorName: String,
    val createdAt: String,
    val isPendingLocal: Boolean = false
)

@Entity(tableName = "cached_history")
data class CachedHistoryEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val summary: String,
    val reason: String,
    val actorName: String,
    val changedAt: String,
    val isPendingLocal: Boolean = false
)

@Entity(tableName = "cached_users")
data class CachedUserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val role: String
)

@Entity(tableName = "cached_lifecycle")
data class CachedLifecycleEntity(
    @PrimaryKey val key: String = "default",
    val statusesJson: String,
    val allowedTransitionsJson: String
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val orderId: String?,
    val localOrderId: String?,
    val userId: String?,
    val title: String?,
    val description: String?,
    val body: String?,
    val toStatus: String?,
    val createdAt: String
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val lastSuccessfulRefreshAt: String? = null,
    val lastErrorMessage: String? = null
)

@Dao
interface StatusFlowCacheDao {
    @Query("SELECT * FROM cached_orders ORDER BY updatedAt DESC")
    suspend fun listOrders(): List<CachedOrderEntity>

    @Query("SELECT * FROM cached_orders WHERE id = :orderId LIMIT 1")
    suspend fun findOrder(orderId: String): CachedOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(items: List<CachedOrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(item: CachedOrderEntity)

    @Query("DELETE FROM cached_orders WHERE isPendingLocal = 0")
    suspend fun deleteRemoteBackedOrders()

    @Query("DELETE FROM cached_orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query("SELECT * FROM cached_comments WHERE orderId = :orderId ORDER BY createdAt ASC")
    suspend fun listComments(orderId: String): List<CachedCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(items: List<CachedCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComment(item: CachedCommentEntity)

    @Query("DELETE FROM cached_comments WHERE orderId = :orderId AND isPendingLocal = 0")
    suspend fun deleteRemoteBackedComments(orderId: String)

    @Query("SELECT * FROM cached_history WHERE orderId = :orderId ORDER BY changedAt ASC")
    suspend fun listHistory(orderId: String): List<CachedHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(items: List<CachedHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryEntry(item: CachedHistoryEntity)

    @Query("DELETE FROM cached_history WHERE orderId = :orderId AND isPendingLocal = 0")
    suspend fun deleteRemoteBackedHistory(orderId: String)

    @Query("SELECT * FROM cached_users ORDER BY name ASC")
    suspend fun listUsers(): List<CachedUserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(items: List<CachedUserEntity>)

    @Query("DELETE FROM cached_users")
    suspend fun clearUsers()

    @Query("SELECT * FROM cached_lifecycle WHERE key = 'default' LIMIT 1")
    suspend fun getLifecycle(): CachedLifecycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLifecycle(item: CachedLifecycleEntity)

    @Query("SELECT * FROM pending_mutations ORDER BY createdAt ASC")
    suspend fun listPendingMutations(): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutations")
    suspend fun countPendingMutations(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingMutation(item: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun deletePendingMutation(id: String)

    @Query("SELECT * FROM sync_metadata WHERE id = 0 LIMIT 1")
    suspend fun getSyncMetadata(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(item: SyncMetadataEntity)

    @Transaction
    suspend fun replaceRemoteDashboard(
        orders: List<CachedOrderEntity>,
        users: List<CachedUserEntity>,
        lifecycle: CachedLifecycleEntity,
        refreshedAt: String
    ) {
        deleteRemoteBackedOrders()
        clearUsers()
        upsertOrders(orders)
        upsertUsers(users)
        upsertLifecycle(lifecycle)
        upsertSyncMetadata(SyncMetadataEntity(lastSuccessfulRefreshAt = refreshedAt, lastErrorMessage = null))
    }

    @Transaction
    suspend fun replaceRemoteDetail(
        order: CachedOrderEntity,
        comments: List<CachedCommentEntity>,
        history: List<CachedHistoryEntity>
    ) {
        upsertOrder(order)
        deleteRemoteBackedComments(order.id)
        deleteRemoteBackedHistory(order.id)
        upsertComments(comments)
        upsertHistory(history)
    }
}

@Database(
    entities = [
        CachedOrderEntity::class,
        CachedCommentEntity::class,
        CachedHistoryEntity::class,
        CachedUserEntity::class,
        CachedLifecycleEntity::class,
        PendingMutationEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class StatusFlowLocalDatabase : RoomDatabase() {
    abstract fun cacheDao(): StatusFlowCacheDao

    companion object {
        @Volatile
        private var instance: StatusFlowLocalDatabase? = null

        fun getInstance(context: Context): StatusFlowLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StatusFlowLocalDatabase::class.java,
                    "statusflow-mobile.db"
                ).build().also { instance = it }
            }
        }
    }
}

data class MobileSyncState(
    val lastSuccessfulRefreshLabel: String? = null,
    val isUsingCachedData: Boolean = false,
    val pendingMutationCount: Int = 0,
    val lastErrorMessage: String? = null
)

class StatusFlowCacheStore(private val dao: StatusFlowCacheDao) {
    private val gson = Gson()

    suspend fun cacheRemoteDashboard(
        orders: List<OrderApiResponse>,
        users: List<UserApiResponse>,
        lifecycle: OrderStatusLifecycleApiResponse,
        now: String = Instant.now().toString()
    ) {
        dao.replaceRemoteDashboard(
            orders = orders.map { order ->
                CachedOrderEntity(
                    id = order.id,
                    code = order.code,
                    title = order.title,
                    description = "",
                    customerName = order.customer_name,
                    rawStatus = order.status,
                    updatedAt = order.updated_at
                )
            },
            users = users.map { user ->
                CachedUserEntity(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    role = user.role
                )
            },
            lifecycle = CachedLifecycleEntity(
                statusesJson = gson.toJson(lifecycle.statuses),
                allowedTransitionsJson = gson.toJson(lifecycle.allowed_transitions)
            ),
            refreshedAt = now
        )
    }

    suspend fun cacheRemoteDetail(detail: OrderDetailApiResponse) {
        dao.replaceRemoteDetail(
            order = CachedOrderEntity(
                id = detail.id,
                code = detail.code,
                title = detail.title,
                description = detail.description,
                customerName = detail.customer_name,
                rawStatus = detail.status,
                updatedAt = detail.updated_at
            ),
            comments = detail.comments.map { comment ->
                CachedCommentEntity(
                    id = comment.id,
                    orderId = detail.id,
                    body = comment.body,
                    authorName = comment.author.name,
                    createdAt = comment.created_at
                )
            },
            history = detail.history.map { event ->
                CachedHistoryEntity(
                    id = event.id,
                    orderId = detail.id,
                    summary = buildHistorySummary(event.from_status, event.to_status),
                    reason = event.reason.ifBlank { "No reason provided." },
                    actorName = event.changed_by.name,
                    changedAt = event.changed_at
                )
            }
        )
    }

    suspend fun readDashboard(): MobileDashboardData? {
        val orders = dao.listOrders()
        val users = dao.listUsers()
        val lifecycle = dao.getLifecycle()
        if (orders.isEmpty() || users.isEmpty() || lifecycle == null) {
            return null
        }

        val statusesType = object : TypeToken<List<String>>() {}.type
        val transitionsType = object : TypeToken<Map<String, List<String>>>() {}.type
        val allowedTransitions: Map<String, List<String>> =
            gson.fromJson(lifecycle.allowedTransitionsJson, transitionsType)

        return MobileDashboardData(
            orders = orders.map(::mapOrderSummary),
            users = users.map(::mapUserSummary),
            allowedTransitions = allowedTransitions,
            syncState = readSyncState(isUsingCachedData = true)
        )
    }

    suspend fun readOrderDetail(orderId: String): MobileOrderDetail? {
        val order = dao.findOrder(orderId) ?: return null
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
            comments = dao.listComments(orderId).map(::mapComment),
            history = dao.listHistory(orderId).map(::mapHistory)
        )
    }

    suspend fun enqueueCreateOrder(localOrderId: String, code: String, title: String, description: String, customer: MobileUserSummary) {
        val now = Instant.now().toString()
        dao.upsertOrder(
            CachedOrderEntity(
                id = localOrderId,
                code = code,
                title = title,
                description = description,
                customerName = customer.name,
                rawStatus = "new",
                updatedAt = now,
                isPendingLocal = true
            )
        )
        dao.upsertHistoryEntry(
            CachedHistoryEntity(
                id = "pending-history-${UUID.randomUUID()}",
                orderId = localOrderId,
                summary = "Created in New",
                reason = "Queued while offline.",
                actorName = customer.name,
                changedAt = now,
                isPendingLocal = true
            )
        )
        dao.upsertPendingMutation(
            PendingMutationEntity(
                id = UUID.randomUUID().toString(),
                type = "create_order",
                orderId = null,
                localOrderId = localOrderId,
                userId = customer.id,
                title = title,
                description = description,
                body = null,
                toStatus = null,
                createdAt = now
            )
        )
    }

    suspend fun enqueueComment(orderId: String, author: MobileUserSummary, body: String) {
        val now = Instant.now().toString()
        dao.upsertComment(
            CachedCommentEntity(
                id = "pending-comment-${UUID.randomUUID()}",
                orderId = orderId,
                body = body,
                authorName = author.name,
                createdAt = now,
                isPendingLocal = true
            )
        )
        dao.upsertPendingMutation(
            PendingMutationEntity(
                id = UUID.randomUUID().toString(),
                type = "add_comment",
                orderId = orderId,
                localOrderId = null,
                userId = author.id,
                title = null,
                description = null,
                body = body,
                toStatus = null,
                createdAt = now
            )
        )
    }

    suspend fun enqueueStatusTransition(orderId: String, actor: MobileUserSummary, toStatus: String) {
        val now = Instant.now().toString()
        val currentOrder = dao.findOrder(orderId)
        if (currentOrder != null) {
            dao.upsertOrder(
                currentOrder.copy(
                    rawStatus = toStatus,
                    updatedAt = now,
                    isPendingLocal = currentOrder.isPendingLocal
                )
            )
        }
        dao.upsertHistoryEntry(
            CachedHistoryEntity(
                id = "pending-history-${UUID.randomUUID()}",
                orderId = orderId,
                summary = currentOrder?.let { "${statusLabel(it.rawStatus)} -> ${statusLabel(toStatus)}" } ?: "Moved to ${statusLabel(toStatus)}",
                reason = "Queued while offline.",
                actorName = actor.name,
                changedAt = now,
                isPendingLocal = true
            )
        )
        dao.upsertPendingMutation(
            PendingMutationEntity(
                id = UUID.randomUUID().toString(),
                type = "transition_status",
                orderId = orderId,
                localOrderId = null,
                userId = actor.id,
                title = null,
                description = null,
                body = null,
                toStatus = toStatus,
                createdAt = now
            )
        )
    }

    suspend fun listPendingMutations(): List<PendingMutationEntity> = dao.listPendingMutations()

    suspend fun deletePendingMutation(id: String) {
        dao.deletePendingMutation(id)
    }

    suspend fun replaceLocalOrder(localOrderId: String, detail: OrderDetailApiResponse) {
        dao.deleteOrder(localOrderId)
        cacheRemoteDetail(detail)
    }

    suspend fun readSyncState(isUsingCachedData: Boolean): MobileSyncState {
        val metadata = dao.getSyncMetadata()
        return MobileSyncState(
            lastSuccessfulRefreshLabel = metadata?.lastSuccessfulRefreshAt?.let(::formatTimestamp),
            isUsingCachedData = isUsingCachedData,
            pendingMutationCount = dao.countPendingMutations(),
            lastErrorMessage = metadata?.lastErrorMessage
        )
    }

    suspend fun updateSyncError(message: String?) {
        val current = dao.getSyncMetadata() ?: SyncMetadataEntity()
        dao.upsertSyncMetadata(current.copy(lastErrorMessage = message))
    }

    private fun mapOrderSummary(order: CachedOrderEntity): MobileOrderSummary {
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

    private fun mapUserSummary(user: CachedUserEntity): MobileUserSummary {
        return MobileUserSummary(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role
        )
    }

    private fun mapComment(comment: CachedCommentEntity): MobileOrderComment {
        return MobileOrderComment(
            id = comment.id,
            body = comment.body,
            authorName = comment.authorName,
            createdAtLabel = formatTimestamp(comment.createdAt)
        )
    }

    private fun mapHistory(event: CachedHistoryEntity): MobileOrderHistoryEvent {
        return MobileOrderHistoryEvent(
            id = event.id,
            summary = event.summary,
            reason = event.reason,
            actorName = event.actorName,
            changedAtLabel = formatTimestamp(event.changedAt)
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

    private fun statusColor(status: String): androidx.compose.ui.graphics.Color {
        return when (status) {
            "new" -> androidx.compose.ui.graphics.Color(0xFF92B1F7)
            "in_review" -> androidx.compose.ui.graphics.Color(0xFFFFD574)
            "approved" -> androidx.compose.ui.graphics.Color(0xFF8BE0C0)
            "rejected" -> androidx.compose.ui.graphics.Color(0xFFFF9D9D)
            "fulfilled" -> androidx.compose.ui.graphics.Color(0xFF9EFADB)
            "cancelled" -> androidx.compose.ui.graphics.Color(0xFFD2DAE6)
            else -> androidx.compose.ui.graphics.Color.White
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
