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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MobileHomeRoute()
                }
            }
        }
    }
}

data class MobileHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
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

private enum class MobileOrderSortOption { UPDATED_DESC, UPDATED_ASC, TITLE_ASC, STATUS_ASC }

class MobileHomeViewModel(
    private val repository: StatusFlowApiRepository = StatusFlowApiRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MobileHomeUiState())
    val uiState: StateFlow<MobileHomeUiState> = _uiState.asStateFlow()

    init { refresh(true) }

    fun refresh(showLoader: Boolean = true) {
        viewModelScope.launch {
            val currentSelection = _uiState.value.selectedOrderId
            _uiState.value = _uiState.value.copy(
                isLoading = if (showLoader) true else _uiState.value.isLoading,
                isRefreshing = !showLoader,
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
                    isRefreshing = false,
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
                    isRefreshing = false,
                    errorMessage = throwable.message ?: "Unknown network error."
                )
            }
        }
    }

    fun selectOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedOrderId = orderId, errorMessage = null)
            runCatching { repository.fetchOrderDetail(orderId) }
                .onSuccess { detail -> _uiState.value = _uiState.value.copy(selectedOrderDetail = detail) }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Failed to load order details."
                    )
                }
        }
    }

    fun createOrder(title: String, description: String) {
        val customer = uiState.value.users.firstOrNull { it.role == "customer" }
        if (customer == null) {
            _uiState.value = _uiState.value.copy(actionMessage = "Customer seed user is missing.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, actionMessage = null, errorMessage = null)
            runCatching {
                repository.createOrder(title, description, customer.id)
                val dashboard = repository.fetchDashboardData()
                val newestOrder = dashboard.orders.firstOrNull()
                val detail = newestOrder?.id?.let { orderId -> repository.fetchOrderDetail(orderId) }
                Triple(dashboard, newestOrder?.id, detail)
            }.onSuccess { (dashboard, selectedOrderId, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isRefreshing = false,
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
                _uiState.value = _uiState.value.copy(isSubmitting = false, actionMessage = throwable.message ?: "Order creation failed.")
            }
        }
    }

    fun transitionOrder(toStatus: String) {
        val state = uiState.value
        val selectedOrderId = state.selectedOrderId
        val operator = state.users.firstOrNull { it.role == "operator" }
        if (selectedOrderId == null) {
            _uiState.value = state.copy(actionMessage = "Pick an order before changing status.")
            return
        }
        if (operator == null) {
            _uiState.value = state.copy(actionMessage = "Operator seed user is missing.")
            return
        }
        mutateSelectedOrder(
            state = state,
            selectedOrderId = selectedOrderId,
            action = { repository.transitionOrderStatus(selectedOrderId, operator.id, toStatus) },
            successMessage = { detail -> "Order moved to ${detail.statusLabel}." }
        )
    }

    fun addComment(body: String) {
        val state = uiState.value
        val selectedOrderId = state.selectedOrderId
        val operator = state.users.firstOrNull { it.role == "operator" }
        if (selectedOrderId == null) {
            _uiState.value = state.copy(actionMessage = "Pick an order before adding a comment.")
            return
        }
        if (operator == null) {
            _uiState.value = state.copy(actionMessage = "Operator seed user is missing.")
            return
        }
        mutateSelectedOrder(
            state = state,
            selectedOrderId = selectedOrderId,
            action = { repository.addComment(selectedOrderId, operator.id, body) },
            successMessage = { "Comment added successfully." }
        )
    }

    private fun mutateSelectedOrder(
        state: MobileHomeUiState,
        selectedOrderId: String,
        action: suspend () -> Unit,
        successMessage: (MobileOrderDetail) -> String
    ) {
        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, actionMessage = null, errorMessage = null)
            runCatching {
                action()
                val dashboard = repository.fetchDashboardData()
                val detail = repository.fetchOrderDetail(selectedOrderId)
                dashboard to detail
            }.onSuccess { (dashboard, detail) ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isRefreshing = false,
                    isSubmitting = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = successMessage(detail)
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, actionMessage = throwable.message ?: "Action failed.")
            }
        }
    }
}

@Composable
fun MobileHomeRoute() {
    val viewModel: MobileHomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MobileHomeViewModel() as T
    })
    val state by viewModel.uiState.collectAsState()
    MobileHomeScreen(
        state = state,
        onRefresh = { viewModel.refresh(false) },
        onCreateOrder = viewModel::createOrder,
        onSelectOrder = viewModel::selectOrder,
        onTransitionOrder = viewModel::transitionOrder,
        onAddComment = viewModel::addComment
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var isShowingDetail by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOptionName by rememberSaveable { mutableStateOf(MobileOrderSortOption.UPDATED_DESC.name) }
    val sortOption = MobileOrderSortOption.valueOf(sortOptionName)

    val availableStatuses = state.orders.map { it.rawStatus }.distinct()
    val normalizedQuery = searchQuery.trim().lowercase()
    val visibleOrders = state.orders
        .filter { statusFilter == null || it.rawStatus == statusFilter }
        .filter { order ->
            normalizedQuery.isBlank() ||
                order.code.lowercase().contains(normalizedQuery) ||
                order.title.lowercase().contains(normalizedQuery) ||
                order.customerName.lowercase().contains(normalizedQuery)
        }
        .let { orders ->
            when (sortOption) {
                MobileOrderSortOption.UPDATED_DESC -> orders
                MobileOrderSortOption.UPDATED_ASC -> orders.reversed()
                MobileOrderSortOption.TITLE_ASC -> orders.sortedBy { it.title.lowercase() }
                MobileOrderSortOption.STATUS_ASC -> orders.sortedWith(compareBy<MobileOrderSummary> { statusLabel(it.rawStatus) }.thenBy { it.title.lowercase() })
            }
        }

    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF13243E))))
                .padding(padding)
        ) {
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item { ScreenTitle() }
                    item { ApiCard(state.apiBaseUrl) }
                    item {
                        CreateOrderCard(
                            title = title,
                            description = description,
                            isSubmitting = state.isSubmitting,
                            isLoading = state.isLoading || state.isRefreshing,
                            onTitleChange = { title = it },
                            onDescriptionChange = { description = it },
                            onCreate = { onCreateOrder(title.trim(), description.trim()); title = ""; description = "" },
                            onRefresh = onRefresh
                        )
                    }
                    item {
                        ListControlsCard(
                            searchQuery = searchQuery,
                            selectedStatus = statusFilter,
                            availableStatuses = availableStatuses,
                            sortOption = sortOption,
                            onSearchQueryChange = { searchQuery = it },
                            onSelectStatus = { selected -> statusFilter = if (statusFilter == selected) null else selected },
                            onToggleSort = {
                                sortOptionName = when (sortOption) {
                                    MobileOrderSortOption.UPDATED_DESC -> MobileOrderSortOption.UPDATED_ASC
                                    MobileOrderSortOption.UPDATED_ASC -> MobileOrderSortOption.TITLE_ASC
                                    MobileOrderSortOption.TITLE_ASC -> MobileOrderSortOption.STATUS_ASC
                                    MobileOrderSortOption.STATUS_ASC -> MobileOrderSortOption.UPDATED_DESC
                                }.name
                            }
                        )
                    }
                    if (state.actionMessage != null) item { StatusMessageCard("Latest action", state.actionMessage) }

                    when {
                        state.isLoading -> item { StatusMessageCard("Syncing orders", "Fetching the latest order list from the API.") }
                        state.errorMessage != null -> item { StatusMessageCard("Sync failed", state.errorMessage) }
                        else -> {
                            if (isShowingDetail && state.selectedOrderDetail != null) {
                                item {
                                    DetailScreenCard(
                                        detail = state.selectedOrderDetail,
                                        allowedTransitions = state.allowedTransitions[state.selectedOrderDetail.rawStatus].orEmpty(),
                                        isSubmitting = state.isSubmitting,
                                        actionMessage = state.actionMessage,
                                        commentBody = commentBody,
                                        onCommentBodyChange = { commentBody = it },
                                        onTransitionOrder = onTransitionOrder,
                                        onAddComment = { onAddComment(commentBody.trim()) },
                                        onBack = { isShowingDetail = false }
                                    )
                                }
                            } else {
                                if (visibleOrders.isEmpty()) {
                                    item {
                                        EmptyQueueCard(
                                            title = if (state.orders.isEmpty()) "No orders yet" else "No orders match your current view",
                                            body = if (state.orders.isEmpty()) {
                                                "Create the first order from this screen or pull down to refresh when the backend receives new work."
                                            } else {
                                                "Try clearing the current filter, editing the search text, or changing the sort to inspect a different slice of the queue."
                                            }
                                        )
                                    }
                                } else {
                                    items(visibleOrders) { item ->
                                        OrderCard(
                                            order = item,
                                            isSelected = state.selectedOrderId == item.id,
                                            onSelectOrder = {
                                                onSelectOrder(it)
                                                isShowingDetail = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("StatusFlow", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Mobile client connected to the shared API. Create new orders here, inspect a selected order, and move it through the same workflow used by the web dashboard.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFD2DAE6)
        )
    }
}

@Composable
private fun ApiCard(apiBaseUrl: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current API", style = MaterialTheme.typography.labelMedium, color = Color(0xFF92B1F7))
            Text(apiBaseUrl, style = MaterialTheme.typography.bodyLarge, color = Color.White)
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
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create order", style = MaterialTheme.typography.titleMedium, color = Color.White)
            OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !isSubmitting && title.length >= 3, onClick = onCreate) { Text(if (isSubmitting) "Submitting..." else "Create") }
                Button(enabled = !isLoading && !isSubmitting, onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun ListControlsCard(
    searchQuery: String,
    selectedStatus: String?,
    availableStatuses: List<String>,
    sortOption: MobileOrderSortOption,
    onSearchQueryChange: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onToggleSort: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Queue controls", style = MaterialTheme.typography.titleMedium, color = Color.White)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search orders") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Sort: ${sortOptionLabel(sortOption)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleSort) { Text("Change sort") }
                if (selectedStatus != null) Button(onClick = { onSelectStatus(selectedStatus) }) { Text("Clear filter") }
            }
            if (availableStatuses.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableStatuses.forEach { status -> Button(onClick = { onSelectStatus(status) }) { Text(statusLabel(status)) } }
                }
            }
        }
    }
}

@Composable
private fun DetailScreenCard(
    detail: MobileOrderDetail,
    allowedTransitions: List<String>,
    isSubmitting: Boolean,
    actionMessage: String?,
    commentBody: String,
    onCommentBodyChange: (String) -> Unit,
    onTransitionOrder: (String) -> Unit,
    onAddComment: () -> Unit,
    onBack: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C3456))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Back to queue") }
            }
            Text("${detail.code} - ${detail.title}", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Customer: ${detail.customerName}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
            Text("Status: ${detail.statusLabel}", style = MaterialTheme.typography.bodyMedium, color = detail.statusColor)
            Text(detail.description, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text("Updated ${detail.updatedAtLabel}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92B1F7))
            if (actionMessage != null) {
                StatusMessageCard(title = "Latest action", body = actionMessage)
            }
            Text("Next steps", style = MaterialTheme.typography.titleSmall, color = Color.White)
            if (allowedTransitions.isEmpty()) {
                Text("This order is already in a terminal state.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTransitions.forEach { status ->
                        Button(enabled = !isSubmitting, onClick = { onTransitionOrder(status) }) { Text(statusLabel(status)) }
                    }
                }
            }
            Text("History", style = MaterialTheme.typography.titleSmall, color = Color.White)
            detail.history.takeLast(4).reversed().forEach { event ->
                TimelineEntry(event.summary, "${event.actorName} - ${event.changedAtLabel}", event.reason)
            }
            Text("Comments", style = MaterialTheme.typography.titleSmall, color = Color.White)
            OutlinedTextField(value = commentBody, onValueChange = onCommentBodyChange, label = { Text("Add operator note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !isSubmitting && commentBody.trim().isNotEmpty(), onClick = onAddComment) {
                    Text(if (isSubmitting) "Sending..." else "Post comment")
                }
            }
            if (detail.comments.isEmpty()) {
                Text("No comments yet.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
            } else {
                detail.comments.reversed().forEach { comment ->
                    TimelineEntry(comment.authorName, comment.createdAtLabel, comment.body)
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
        }
    }
}

@Composable
private fun OrderCard(order: MobileOrderSummary, isSelected: Boolean, onSelectOrder: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelectOrder(order.id) },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF23406A) else Color(0xFF172A45))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.code, style = MaterialTheme.typography.labelMedium, color = Color(0xFF92B1F7))
            Text(order.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Customer: ${order.customerName}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
            Text("Status: ${order.statusLabel}", style = MaterialTheme.typography.bodyMedium, color = order.statusColor)
            Text("Updated ${order.updatedAtLabel}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92B1F7))
            if (isSelected) Text("Selected for detail view", style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

@Composable
private fun TimelineEntry(title: String, meta: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF13243E))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Color(0xFF92B1F7))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
        }
    }
}

@Composable
private fun StatusMessageCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2DAE6))
        }
    }
}

private fun statusLabel(status: String): String = status.split("_").joinToString(" ") {
    it.replaceFirstChar { character -> character.uppercase() }
}

private fun sortOptionLabel(option: MobileOrderSortOption): String = when (option) {
    MobileOrderSortOption.UPDATED_DESC -> "Newest first"
    MobileOrderSortOption.UPDATED_ASC -> "Oldest first"
    MobileOrderSortOption.TITLE_ASC -> "Title A-Z"
    MobileOrderSortOption.STATUS_ASC -> "Status"
}
