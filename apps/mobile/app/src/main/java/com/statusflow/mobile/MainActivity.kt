package com.statusflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statusflow.mobile.data.MobileOrderDetail
import com.statusflow.mobile.data.MobileOrderSummary
import com.statusflow.mobile.data.MobileUserSummary
import com.statusflow.mobile.data.StatusFlowApiRepository
import com.statusflow.mobile.ui.theme.StatusFlowTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StatusFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MobileHomeRoute()
                }
            }
        }
    }
}

data class MobileHomeUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val orders: List<MobileOrderSummary> = emptyList(),
    val users: List<MobileUserSummary> = emptyList(),
    val allowedTransitions: Map<String, List<String>> = emptyMap(),
    val selectedOrderId: String? = null,
    val selectedOrderDetail: MobileOrderDetail? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

private enum class MobileOrderSortOption {
    UPDATED_DESC,
    UPDATED_ASC,
    TITLE_ASC,
    STATUS_ASC
}

class MobileHomeViewModel(
    private val repository: StatusFlowApiRepository = StatusFlowApiRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MobileHomeUiState())
    val uiState: StateFlow<MobileHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val currentSelection = _uiState.value.selectedOrderId
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                actionMessage = null
            )

            runCatching {
                val dashboard = repository.fetchDashboardData()
                val selectedOrderId = currentSelection ?: dashboard.orders.firstOrNull()?.id
                val detail = selectedOrderId?.let { orderId -> repository.fetchOrderDetail(orderId) }

                Triple(dashboard, selectedOrderId, detail)
            }.onSuccess { (dashboard, selectedOrderId, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Unknown network error."
                )
            }
        }
    }

    fun selectOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedOrderId = orderId,
                errorMessage = null
            )

            runCatching { repository.fetchOrderDetail(orderId) }
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(selectedOrderDetail = detail)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Failed to load order details."
                    )
                }
        }
    }

    fun createOrder(title: String, description: String) {
        val customer = uiState.value.users.firstOrNull { user -> user.role == "customer" }

        if (customer == null) {
            _uiState.value = _uiState.value.copy(
                actionMessage = "Customer seed user is missing."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                actionMessage = null,
                errorMessage = null
            )

            runCatching {
                repository.createOrder(
                    title = title,
                    description = description,
                    customerId = customer.id
                )
                val dashboard = repository.fetchDashboardData()
                val newestOrder = dashboard.orders.firstOrNull()
                val detail = newestOrder?.id?.let { orderId -> repository.fetchOrderDetail(orderId) }

                Triple(dashboard, newestOrder?.id, detail)
            }.onSuccess { (dashboard, selectedOrderId, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isSubmitting = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = "Order created successfully."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = throwable.message ?: "Order creation failed."
                )
            }
        }
    }

    fun transitionOrder(toStatus: String) {
        val state = uiState.value
        val selectedOrderId = state.selectedOrderId
        val operator = state.users.firstOrNull { user -> user.role == "operator" }

        if (selectedOrderId == null) {
            _uiState.value = state.copy(actionMessage = "Pick an order before changing status.")
            return
        }

        if (operator == null) {
            _uiState.value = state.copy(actionMessage = "Operator seed user is missing.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isSubmitting = true,
                actionMessage = null,
                errorMessage = null
            )

            runCatching {
                repository.transitionOrderStatus(
                    orderId = selectedOrderId,
                    changedById = operator.id,
                    toStatus = toStatus
                )
                val dashboard = repository.fetchDashboardData()
                val detail = repository.fetchOrderDetail(selectedOrderId)

                dashboard to detail
            }.onSuccess { (dashboard, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isSubmitting = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = "Order moved to ${detail.statusLabel}."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = throwable.message ?: "Status transition failed."
                )
            }
        }
    }

    fun addComment(body: String) {
        val state = uiState.value
        val selectedOrderId = state.selectedOrderId
        val operator = state.users.firstOrNull { user -> user.role == "operator" }

        if (selectedOrderId == null) {
            _uiState.value = state.copy(actionMessage = "Pick an order before adding a comment.")
            return
        }

        if (operator == null) {
            _uiState.value = state.copy(actionMessage = "Operator seed user is missing.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isSubmitting = true,
                actionMessage = null,
                errorMessage = null
            )

            runCatching {
                repository.addComment(
                    orderId = selectedOrderId,
                    authorId = operator.id,
                    body = body
                )
                val dashboard = repository.fetchDashboardData()
                val detail = repository.fetchOrderDetail(selectedOrderId)

                dashboard to detail
            }.onSuccess { (dashboard, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isSubmitting = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = "Comment added successfully."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = throwable.message ?: "Comment submission failed."
                )
            }
        }
    }
}

@Composable
fun MobileHomeRoute() {
    val viewModel: MobileHomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MobileHomeViewModel() as T
        }
    )
    val state by viewModel.uiState.collectAsState()
    MobileHomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onCreateOrder = viewModel::createOrder,
        onSelectOrder = viewModel::selectOrder,
        onTransitionOrder = viewModel::transitionOrder,
        onAddComment = viewModel::addComment
    )
}

@Composable
fun MobileHomeScreen(
    state: MobileHomeUiState,
    onRefresh: () -> Unit,
    onCreateOrder: (String, String) -> Unit,
    onSelectOrder: (String) -> Unit,
    onTransitionOrder: (String) -> Unit,
    onAddComment: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var commentBody by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(MobileOrderSortOption.UPDATED_DESC) }

    val availableStatuses = state.orders.map { order -> order.rawStatus }.distinct()
    val visibleOrders = state.orders
        .filter { order -> statusFilter == null || order.rawStatus == statusFilter }
        .let { orders ->
            when (sortOption) {
                MobileOrderSortOption.UPDATED_DESC -> orders
                MobileOrderSortOption.UPDATED_ASC -> orders.reversed()
                MobileOrderSortOption.TITLE_ASC -> orders.sortedBy { order -> order.title.lowercase() }
                MobileOrderSortOption.STATUS_ASC -> orders.sortedWith(
                    compareBy<MobileOrderSummary> { order -> statusLabel(order.rawStatus) }
                        .thenBy { order -> order.title.lowercase() }
                )
            }
        }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0B1220), Color(0xFF13243E))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "StatusFlow",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Text(
                        text = "Mobile client connected to the shared API. Create new orders here, inspect a selected order, and move it through the same workflow used by the web dashboard.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFD2DAE6)
                    )
                }
                item {
                    ApiCard(state.apiBaseUrl)
                }
                item {
                    CreateOrderCard(
                        title = title,
                        description = description,
                        isSubmitting = state.isSubmitting,
                        isLoading = state.isLoading,
                        onTitleChange = { title = it },
                        onDescriptionChange = { description = it },
                        onCreate = {
                            onCreateOrder(title.trim(), description.trim())
                            title = ""
                            description = ""
                        },
                        onRefresh = onRefresh
                    )
                }
                item {
                    ListControlsCard(
                        selectedStatus = statusFilter,
                        availableStatuses = availableStatuses,
                        sortOption = sortOption,
                        onSelectStatus = { selected ->
                            statusFilter = if (statusFilter == selected) null else selected
                        },
                        onToggleSort = {
                            sortOption = when (sortOption) {
                                MobileOrderSortOption.UPDATED_DESC -> MobileOrderSortOption.UPDATED_ASC
                                MobileOrderSortOption.UPDATED_ASC -> MobileOrderSortOption.TITLE_ASC
                                MobileOrderSortOption.TITLE_ASC -> MobileOrderSortOption.STATUS_ASC
                                MobileOrderSortOption.STATUS_ASC -> MobileOrderSortOption.UPDATED_DESC
                            }
                        }
                    )
                }

                if (state.actionMessage != null) {
                    item {
                        StatusMessageCard(
                            title = "Latest action",
                            body = state.actionMessage
                        )
                    }
                }

                when {
                    state.isLoading -> {
                        item {
                            StatusMessageCard(
                                title = "Syncing orders",
                                body = "Fetching the latest order list from the API."
                            )
                        }
                    }

                    state.errorMessage != null -> {
                        item {
                            StatusMessageCard(
                                title = "Sync failed",
                                body = state.errorMessage
                            )
                        }
                    }

                    else -> {
                        item {
                            DetailCard(
                                detail = state.selectedOrderDetail,
                                allowedTransitions = state.selectedOrderDetail?.let { detail ->
                                    state.allowedTransitions[detail.rawStatus].orEmpty()
                                }.orEmpty(),
                                isSubmitting = state.isSubmitting,
                                commentBody = commentBody,
                                onCommentBodyChange = { commentBody = it },
                                onTransitionOrder = onTransitionOrder,
                                onAddComment = { onAddComment(commentBody.trim()) }
                            )
                        }

                        items(visibleOrders) { item ->
                            OrderCard(
                                order = item,
                                isSelected = state.selectedOrderId == item.id,
                                onSelectOrder = onSelectOrder
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiCard(apiBaseUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Current API",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF92B1F7)
            )
            Text(
                text = apiBaseUrl,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CreateOrderCard(
    title: String,
    description: String,
    isSubmitting: Boolean,
    isLoading: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create order",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !isSubmitting && title.length >= 3,
                    onClick = onCreate
                ) {
                    Text(if (isSubmitting) "Submitting..." else "Create")
                }
                Button(
                    enabled = !isLoading && !isSubmitting,
                    onClick = onRefresh
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun ListControlsCard(
    selectedStatus: String?,
    availableStatuses: List<String>,
    sortOption: MobileOrderSortOption,
    onSelectStatus: (String) -> Unit,
    onToggleSort: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Queue controls",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Sort: ${sortOptionLabel(sortOption)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2DAE6)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleSort) {
                    Text("Change sort")
                }
                if (selectedStatus != null) {
                    Button(onClick = { onSelectStatus(selectedStatus) }) {
                        Text("Clear filter")
                    }
                }
            }
            if (availableStatuses.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableStatuses.forEach { status ->
                        Button(onClick = { onSelectStatus(status) }) {
                            Text(statusLabel(status))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    detail: MobileOrderDetail?,
    allowedTransitions: List<String>,
    isSubmitting: Boolean,
    commentBody: String,
    onCommentBodyChange: (String) -> Unit,
    onTransitionOrder: (String) -> Unit,
    onAddComment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C3456))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (detail == null) {
                Text(
                    text = "Order detail",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Select an order below to inspect its description, comments, and status history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2DAE6)
                )
                return@Column
            }

            Text(
                text = "${detail.code} - ${detail.title}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Customer: ${detail.customerName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2DAE6)
            )
            Text(
                text = "Status: ${detail.statusLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = detail.statusColor
            )
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = "Updated ${detail.updatedAtLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92B1F7)
            )

            Text(
                text = "Next steps",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            if (allowedTransitions.isEmpty()) {
                Text(
                    text = "This order is already in a terminal state.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2DAE6)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTransitions.forEach { status ->
                        Button(
                            enabled = !isSubmitting,
                            onClick = { onTransitionOrder(status) }
                        ) {
                            Text(statusLabel(status))
                        }
                    }
                }
            }

            Text(
                text = "History",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            detail.history.takeLast(4).reversed().forEach { event ->
                TimelineEntry(
                    title = event.summary,
                    meta = "${event.actorName} - ${event.changedAtLabel}",
                    body = event.reason
                )
            }

            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            OutlinedTextField(
                value = commentBody,
                onValueChange = onCommentBodyChange,
                label = { Text("Add operator note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isSubmitting && commentBody.trim().isNotEmpty(),
                    onClick = onAddComment
                ) {
                    Text(if (isSubmitting) "Sending..." else "Post comment")
                }
            }
            if (detail.comments.isEmpty()) {
                Text(
                    text = "No comments yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2DAE6)
                )
            } else {
                detail.comments.reversed().forEach { comment ->
                    TimelineEntry(
                        title = comment.authorName,
                        meta = comment.createdAtLabel,
                        body = comment.body
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: MobileOrderSummary,
    isSelected: Boolean,
    onSelectOrder: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectOrder(order.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF23406A) else Color(0xFF172A45)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = order.code,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF92B1F7)
            )
            Text(
                text = order.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Customer: ${order.customerName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2DAE6)
            )
            Text(
                text = "Status: ${order.statusLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = order.statusColor
            )
            Text(
                text = "Updated ${order.updatedAtLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92B1F7)
            )
            if (isSelected) {
                Text(
                    text = "Selected for detail view",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TimelineEntry(title: String, meta: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13243E))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92B1F7)
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2DAE6)
            )
        }
    }
}

@Composable
private fun StatusMessageCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD2DAE6)
            )
        }
    }
}

private fun statusLabel(status: String): String {
    return status.split("_").joinToString(" ") { token ->
        token.replaceFirstChar { character -> character.uppercase() }
    }
}

private fun sortOptionLabel(option: MobileOrderSortOption): String {
    return when (option) {
        MobileOrderSortOption.UPDATED_DESC -> "Newest first"
        MobileOrderSortOption.UPDATED_ASC -> "Oldest first"
        MobileOrderSortOption.TITLE_ASC -> "Title A-Z"
        MobileOrderSortOption.STATUS_ASC -> "Status"
    }
}
