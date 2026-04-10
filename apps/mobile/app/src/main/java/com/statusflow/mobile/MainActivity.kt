package com.statusflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statusflow.mobile.data.MobileOrderDetail
import com.statusflow.mobile.data.MobileOrderSummary
import com.statusflow.mobile.data.MobileUserSummary
import com.statusflow.mobile.data.StatusFlowApiRepository
import com.statusflow.mobile.ui.theme.Amber300
import com.statusflow.mobile.ui.theme.Blue300
import com.statusflow.mobile.ui.theme.Blue400
import com.statusflow.mobile.ui.theme.Mint400
import com.statusflow.mobile.ui.theme.Navy500
import com.statusflow.mobile.ui.theme.Navy600
import com.statusflow.mobile.ui.theme.Navy700
import com.statusflow.mobile.ui.theme.Navy900
import com.statusflow.mobile.ui.theme.Red300
import com.statusflow.mobile.ui.theme.Slate100
import com.statusflow.mobile.ui.theme.Slate200
import com.statusflow.mobile.ui.theme.Slate300
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
            _uiState.value = _uiState.value.copy(
                selectedOrderId = orderId,
                selectedOrderDetail = null,
                errorMessage = null
            )
            runCatching { repository.fetchOrderDetail(orderId) }
                .onSuccess { detail -> _uiState.value = _uiState.value.copy(selectedOrderDetail = detail) }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        selectedOrderDetail = null,
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
    var isCreateExpanded by rememberSaveable { mutableStateOf(false) }
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
                .background(
                    Brush.verticalGradient(
                        listOf(Navy900, Navy700, Navy600)
                    )
                )
                .padding(padding)
        ) {
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item { ScreenTitle() }
                    item {
                        QueueOverviewCard(
                            totalOrders = state.orders.size,
                            visibleOrders = visibleOrders.size,
                            selectedOrderCode = state.selectedOrderDetail?.code,
                            activeFilterLabel = statusFilter?.let(::statusLabel)
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
                    if (state.actionMessage != null) {
                        item {
                            FeedbackCard(
                                title = "Latest action",
                                body = state.actionMessage,
                                accent = Mint400,
                                surface = Navy700,
                                eyebrow = "ACTION"
                            )
                        }
                    }

                    when {
                        state.isLoading -> {
                            item {
                                FeedbackCard(
                                    title = "Syncing orders",
                                    body = "Fetching the latest order list from the API and rebuilding the queue snapshot.",
                                    accent = Blue300,
                                    surface = Navy500,
                                    eyebrow = "LIVE SYNC"
                                )
                            }
                        }
                        state.errorMessage != null -> {
                            item {
                                FeedbackCard(
                                    title = "Sync failed",
                                    body = state.errorMessage,
                                    accent = Red300,
                                    surface = Navy700,
                                    eyebrow = "ERROR"
                                )
                            }
                        }
                        else -> {
                            if (isShowingDetail && state.selectedOrderId != null) {
                                item {
                                    QueueSectionHeader(
                                        title = if (state.selectedOrderDetail != null) "Selected order" else "Order detail",
                                        subtitle = if (state.selectedOrderDetail != null) {
                                            "Review details, update status, and leave context for the next operator."
                                        } else {
                                            "Recover gracefully when a selected order is temporarily unavailable."
                                        }
                                    )
                                }
                                item {
                                    if (state.selectedOrderDetail != null) {
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
                                    } else {
                                        DetailUnavailableCard(
                                            errorMessage = state.errorMessage,
                                            onBack = { isShowingDetail = false },
                                            onRefresh = onRefresh
                                        )
                                    }
                                }
                            } else {
                                item {
                                    QueueSectionHeader(
                                        title = "Active queue",
                                        subtitle = "Tap any card to move from scan mode into detail mode."
                                    )
                                }
                                if (visibleOrders.isEmpty()) {
                                    item {
                                        EmptyQueueCard(
                                            title = if (state.orders.isEmpty()) "No orders yet" else "No orders match your current view",
                                            body = if (state.orders.isEmpty()) {
                                                "Create the first order from this screen or pull down to refresh when the backend receives new work."
                                            } else {
                                                "Try clearing the current filter, editing the search text, or changing the sort to inspect a different slice of the queue."
                                            },
                                            eyebrow = if (state.orders.isEmpty()) "EMPTY QUEUE" else "FILTERED VIEW",
                                            accent = if (state.orders.isEmpty()) Blue300 else Amber300
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
                    item {
                        CreateOrderCard(
                            title = title,
                            description = description,
                            isExpanded = isCreateExpanded,
                            isSubmitting = state.isSubmitting,
                            isLoading = state.isLoading || state.isRefreshing,
                            onTitleChange = { title = it },
                            onDescriptionChange = { description = it },
                            onCreate = {
                                onCreateOrder(title.trim(), description.trim())
                                title = ""
                                description = ""
                                isCreateExpanded = false
                            },
                            onRefresh = onRefresh,
                            onToggleExpanded = { isCreateExpanded = !isCreateExpanded }
                        )
                    }
                    item { ApiCard(state.apiBaseUrl) }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "MOBILE OPS",
            style = MaterialTheme.typography.labelLarge,
            color = Blue300,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "StatusFlow",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Queue-first control for the same shared workflow used on web.",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate300
        )
    }
}

@Composable
private fun ApiCard(apiBaseUrl: String) {
    ShellCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("Connected backend", style = MaterialTheme.typography.labelMedium, color = Blue300)
                Text(apiBaseUrl, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Live",
                style = MaterialTheme.typography.labelMedium,
                color = Mint400,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QueueOverviewCard(
    totalOrders: Int,
    visibleOrders: Int,
    selectedOrderCode: String?,
    activeFilterLabel: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Navy500),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Blue300.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Queue snapshot", style = MaterialTheme.typography.labelLarge, color = Mint400, fontWeight = FontWeight.SemiBold)
                Text("Active workload first.", style = MaterialTheme.typography.bodyMedium, color = Slate100)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(
                    label = "Total",
                    value = totalOrders.toString(),
                    accent = Blue400,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Visible",
                    value = visibleOrders.toString(),
                    accent = Mint400,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactInfoCard(
                    title = "Focus",
                    value = selectedOrderCode ?: "None",
                    modifier = Modifier.weight(1f)
                )
                CompactInfoCard(
                    title = "Filter",
                    value = activeFilterLabel ?: "All",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CreateOrderCard(
    title: String,
    description: String,
    isExpanded: Boolean,
    isSubmitting: Boolean,
    isLoading: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    ShellCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("Create order", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isExpanded) "Capture new work without leaving the queue."
                        else "Open a compact composer when you need to add work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate300
                    )
                }
                Button(
                    enabled = !isSubmitting,
                    onClick = onToggleExpanded,
                    modifier = Modifier.semantics {
                        stateDescription = if (isExpanded) "Composer expanded" else "Composer collapsed"
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpanded) Navy700 else Blue400,
                        contentColor = if (isExpanded) Slate100 else Navy900,
                        disabledContainerColor = Navy600,
                        disabledContentColor = Slate300
                    )
                ) {
                    Text(if (isExpanded) "Collapse" else "New order")
                }
            }

            if (isExpanded) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Order title") },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Order title"
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Operator brief") },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Operator brief"
                    },
                    minLines = 3
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isSubmitting && title.length >= 3,
                        onClick = onCreate,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue400,
                            contentColor = Navy900,
                            disabledContainerColor = Navy600,
                            disabledContentColor = Slate300
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isSubmitting) "Submitting..." else "Create")
                    }
                    Button(
                        enabled = !isLoading && !isSubmitting,
                        onClick = onRefresh,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate300.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Navy700,
                            contentColor = Slate100,
                            disabledContainerColor = Navy600,
                            disabledContentColor = Slate300
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }
                }
            } else {
                Button(
                    enabled = !isLoading && !isSubmitting,
                    onClick = onRefresh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Slate300.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Navy700,
                        contentColor = Slate100,
                        disabledContainerColor = Navy600,
                        disabledContentColor = Slate300
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate300)
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
    ShellCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(title = "Queue controls", subtitle = "Slice the queue fast when volume rises.")
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search code, title, or customer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort", style = MaterialTheme.typography.labelSmall, color = Slate300)
                Text(sortOptionLabel(sortOption), style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PillButton(
                    label = "Sort",
                    onClick = onToggleSort,
                    accent = Blue400,
                    stateDescription = sortOptionLabel(sortOption),
                    modifier = Modifier.weight(1f)
                )
                if (selectedStatus != null) {
                    PillButton(
                        label = "Clear filter",
                        onClick = { onSelectStatus(selectedStatus) },
                        accent = Slate200,
                        stateDescription = "Status filter active",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (availableStatuses.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status filter", style = MaterialTheme.typography.labelSmall, color = Slate300)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                        items(availableStatuses) { status ->
                            val active = selectedStatus == status
                            Button(
                                onClick = { onSelectStatus(status) },
                                modifier = Modifier.semantics {
                                    stateDescription = if (active) "Selected" else "Not selected"
                                },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, if (active) Blue300 else Slate300.copy(alpha = 0.26f)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) Blue400.copy(alpha = 0.22f) else Navy700,
                                    contentColor = if (active) Color.White else Slate100
                                )
                            ) {
                                Text(statusLabel(status), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy500),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Blue300.copy(alpha = 0.28f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Slate100)
                ) {
                    Text("Back to queue")
                }
                Text(detail.code, style = MaterialTheme.typography.labelLarge, color = Blue300, fontWeight = FontWeight.SemiBold)
                Text(detail.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(label = detail.statusLabel, accent = detail.statusColor)
                    Text("Updated ${detail.updatedAtLabel}", style = MaterialTheme.typography.bodySmall, color = Slate200)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactInfoCard(
                        title = "Customer",
                        value = detail.customerName,
                        modifier = Modifier.weight(1f)
                    )
                    CompactInfoCard(
                        title = "Comments",
                        value = detail.comments.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(detail.description, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        }

        if (actionMessage != null) {
            FeedbackCard(
                title = "Latest action",
                body = actionMessage,
                accent = Mint400,
                surface = Navy700,
                eyebrow = "ACTION"
            )
        }

        DetailSectionCard(title = "Next steps", subtitle = "Move the order through the allowed lifecycle only.") {
            if (allowedTransitions.isEmpty()) {
                Text("This order is already in a terminal state.", style = MaterialTheme.typography.bodyMedium, color = Slate200)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTransitions.forEach { status ->
                        Button(
                            enabled = !isSubmitting,
                            onClick = { onTransitionOrder(status) },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue400,
                                contentColor = Navy900,
                                disabledContainerColor = Navy600,
                                disabledContentColor = Slate300
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(statusLabel(status))
                        }
                    }
                }
            }
        }

        DetailSectionCard(title = "History", subtitle = "Most recent workflow events.") {
            detail.history.takeLast(4).reversed().forEach { event ->
                TimelineEntry(event.summary, "${event.actorName} | ${event.changedAtLabel}", event.reason)
            }
        }

        DetailSectionCard(title = "Comments", subtitle = "Leave a note for the next operator.") {
            Button(
                enabled = !isSubmitting && commentBody.trim().isNotEmpty(),
                onClick = onAddComment,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mint400,
                    contentColor = Navy900,
                    disabledContainerColor = Navy600,
                    disabledContentColor = Slate300
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSubmitting) "Sending..." else "Post comment")
            }
            OutlinedTextField(
                value = commentBody,
                onValueChange = onCommentBodyChange,
                label = { Text("Add operator note") },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Add operator note"
                },
                minLines = 2
            )
            if (detail.comments.isEmpty()) {
                Text("No comments yet.", style = MaterialTheme.typography.bodyMedium, color = Slate200)
            } else {
                detail.comments.reversed().forEach { comment ->
                    TimelineEntry(comment.authorName, comment.createdAtLabel, comment.body)
                }
            }
        }
    }
}

@Composable
private fun DetailUnavailableCard(errorMessage: String?, onBack: () -> Unit, onRefresh: () -> Unit) {
    ShellCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeedbackCard(
                title = "Selected order is unavailable",
                body = errorMessage ?: "The selected order could not be loaded right now. Refresh the queue or go back to pick another item.",
                accent = Amber300,
                surface = Navy700,
                eyebrow = "DETAIL STATE"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Slate100),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back to queue")
                }
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue400, contentColor = Navy900),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(title: String, body: String, eyebrow: String, accent: Color) {
    ShellCard {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Slate200)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactInfoCard(
                    title = "Next move",
                    value = if (eyebrow == "EMPTY QUEUE") "Create order" else "Clear filters",
                    modifier = Modifier.weight(1f)
                )
                CompactInfoCard(
                    title = "Mode",
                    value = if (eyebrow == "EMPTY QUEUE") "Fresh start" else "Narrow slice",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: MobileOrderSummary, isSelected: Boolean, onSelectOrder: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "Open order ${order.code} for ${order.title}"
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .clickable { onSelectOrder(order.id) },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Navy500 else Navy700),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isSelected) Blue300.copy(alpha = 0.55f) else Slate300.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(order.code, style = MaterialTheme.typography.labelLarge, color = Blue300, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isSelected) "Ready in focus lane" else "Tap to open details",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Mint400 else Slate300
                    )
                }
                StatusBadge(label = order.statusLabel, accent = order.statusColor)
            }
            Text(
                order.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactMetaPill(
                    label = "Customer",
                    value = order.customerName,
                    modifier = Modifier.weight(1f)
                )
                CompactMetaPill(
                    label = "Updated",
                    value = compactTimestampLabel(order.updatedAtLabel),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isSelected) {
                FeedbackInline(label = "Selected for detail view", accent = Mint400)
            }
        }
    }
}

@Composable
private fun TimelineEntry(title: String, meta: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Navy700.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Blue300)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Slate200)
        }
    }
}

@Composable
private fun FeedbackCard(title: String, body: String, accent: Color, surface: Color, eyebrow: String) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
        },
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Slate100)
        }
    }
}

@Composable
private fun DetailSectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    ShellCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(title = title, subtitle = subtitle)
            content()
        }
    }
}

@Composable
private fun ShellCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Navy700.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Slate300.copy(alpha = 0.18f))
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Slate300)
    }
}

@Composable
private fun MetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Navy700.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Slate300)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Navy700.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Slate300)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactMetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Navy900.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Slate300)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Slate100, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedbackInline(label: String, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent, RoundedCornerShape(999.dp))
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    stateDescription: String? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier.semantics {
                stateDescription?.let { this.stateDescription = it }
            }
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = Slate100)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusBadge(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .semantics { contentDescription = "Status $label" }
            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusLabel(status: String): String = status.split("_").joinToString(" ") {
    it.replaceFirstChar { character -> character.uppercase() }
}

private fun compactTimestampLabel(label: String): String {
    val parts = label.split(",").map { it.trim() }
    return if (parts.size >= 3) "${parts[0]}, ${parts[2]}" else label
}

private fun sortOptionLabel(option: MobileOrderSortOption): String = when (option) {
    MobileOrderSortOption.UPDATED_DESC -> "Newest first"
    MobileOrderSortOption.UPDATED_ASC -> "Oldest first"
    MobileOrderSortOption.TITLE_ASC -> "Title A-Z"
    MobileOrderSortOption.STATUS_ASC -> "Status"
}
