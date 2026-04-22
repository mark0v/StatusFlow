package com.statusflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
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
import com.statusflow.mobile.data.MobileSessionStore
import com.statusflow.mobile.data.MobileSessionSummary
import com.statusflow.mobile.data.MobileSyncState
import com.statusflow.mobile.data.MobileUserSummary
import com.statusflow.mobile.data.MobileSessionExpiredException
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
        MobileSessionStore.initialize(applicationContext)

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
    val isAuthenticating: Boolean = false,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val session: MobileSessionSummary? = null,
    val orders: List<MobileOrderSummary> = emptyList(),
    val users: List<MobileUserSummary> = emptyList(),
    val syncState: MobileSyncState = MobileSyncState(),
    val allowedTransitions: Map<String, List<String>> = emptyMap(),
    val selectedOrderId: String? = null,
    val selectedOrderDetail: MobileOrderDetail? = null,
    val errorMessage: String? = null,
    val authMessage: String? = null,
    val actionMessage: String? = null
) {
    val isAuthenticated: Boolean get() = session != null
    val isOperator: Boolean get() = session?.role == "operator"
    val canCreateOrders: Boolean get() = session != null
}

internal enum class MobileOrderSortOption { UPDATED_DESC, UPDATED_ASC, TITLE_ASC, STATUS_ASC }

private enum class MobileScreenMode { Queue, Detail, Create }

object MobileUiTags {
    const val LOGIN_CARD = "login_card"
    const val LOGIN_EMAIL_INPUT = "login_email_input"
    const val LOGIN_PASSWORD_INPUT = "login_password_input"
    const val LOGIN_SUBMIT = "login_submit"
    const val SCREEN_TITLE = "screen_title"
    const val SCROLL_CONTENT = "scroll_content"
    const val QUEUE_OVERVIEW = "queue_overview"
    const val LIST_CONTROLS = "list_controls"
    const val SEARCH_INPUT = "search_input"
    const val SORT_BUTTON = "sort_button"
    const val CREATE_CARD = "create_card"
    const val CREATE_TOGGLE = "create_toggle"
    const val CREATE_TITLE_INPUT = "create_title_input"
    const val CREATE_DESCRIPTION_INPUT = "create_description_input"
    const val CREATE_SUBMIT = "create_submit"
    const val DETAIL_SCREEN = "detail_screen"
    const val DETAIL_UNAVAILABLE = "detail_unavailable"
    const val COMMENT_INPUT = "comment_input"
    const val COMMENT_SUBMIT = "comment_submit"
    const val EMPTY_QUEUE = "empty_queue"

    fun orderCard(code: String) = "order_card_$code"
    fun statusChip(status: String) = "status_chip_$status"
}

class MobileHomeViewModel(
    private val repository: StatusFlowApiRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MobileHomeUiState())
    val uiState: StateFlow<MobileHomeUiState> = _uiState.asStateFlow()

    init {
        val storedSession = repository.getStoredSession()
        _uiState.value = _uiState.value.copy(
            isLoading = storedSession != null,
            session = storedSession
        )
        if (storedSession != null) {
            refresh(true)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAuthenticating = true,
                authMessage = null,
                errorMessage = null,
                actionMessage = null
            )
            runCatching {
                repository.login(email, password)
            }.onSuccess { session ->
                _uiState.value = MobileHomeUiState(
                    isLoading = true,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    session = session
                )
                refresh(true)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    authMessage = throwable.message ?: "Sign-in failed. Check your credentials and try again."
                )
            }
        }
    }

    fun signOut() {
        repository.clearSession()
        _uiState.value = MobileHomeUiState(
            isLoading = false,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            authMessage = "Signed out. Sign back in to continue."
        )
    }

    fun refresh(showLoader: Boolean = true) {
        if (!_uiState.value.isAuthenticated) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = null
            )
            return
        }
        viewModelScope.launch {
            val currentState = _uiState.value
            val currentSelection = currentState.selectedOrderId
            _uiState.value = _uiState.value.copy(
                isLoading = if (showLoader) true else _uiState.value.isLoading,
                isRefreshing = !showLoader,
                errorMessage = null,
                authMessage = null,
                actionMessage = null
            )
            runCatching {
                val dashboard = repository.fetchDashboardData()
                val selectedOrderId = currentSelection ?: dashboard.orders.firstOrNull()?.id
                val detail = selectedOrderId?.let { orderId -> repository.fetchOrderDetail(orderId) }
                Triple(dashboard, selectedOrderId, detail)
            }.onSuccess { (dashboard, selectedOrderId, detail) ->
                _uiState.value = authenticatedState(
                    previousState = currentState,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    syncState = dashboard.syncState,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail
                )
            }.onFailure { throwable ->
                if (isUnauthorized(throwable)) {
                    repository.clearSession()
                    _uiState.value = MobileHomeUiState(
                        isLoading = false,
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        authMessage = "Your session expired. Sign in again to continue."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = throwable.message ?: "Unknown network error."
                    )
                }
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
        val state = uiState.value
        val session = state.session
        if (session == null) {
            _uiState.value = state.copy(authMessage = "Sign in before creating an order.")
            return
        }
        val customer = if (session.role == "customer") {
            state.users.firstOrNull { it.email == session.email } ?: state.users.firstOrNull { it.role == "customer" }
        } else {
            state.users.firstOrNull { it.role == "customer" }
        }
        if (customer == null) {
            _uiState.value = state.copy(actionMessage = "Customer account is unavailable.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, actionMessage = null, errorMessage = null)
            runCatching {
                val result = repository.createOrder(title, description, customer.id)
                val dashboard = repository.fetchDashboardData()
                val newestOrder = dashboard.orders.firstOrNull()
                val detail = newestOrder?.id?.let { orderId -> repository.fetchOrderDetail(orderId) }
                listOf(result, dashboard, newestOrder?.id, detail)
            }.onSuccess { (resultAny, dashboardAny, selectedOrderIdAny, detailAny) ->
                val result = resultAny as com.statusflow.mobile.data.MobileMutationResult
                val dashboard = dashboardAny as com.statusflow.mobile.data.MobileDashboardData
                val selectedOrderId = selectedOrderIdAny as String?
                val detail = detailAny as MobileOrderDetail?
                _uiState.value = authenticatedState(
                    previousState = state,
                    isSubmitting = false,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    syncState = dashboard.syncState,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = when {
                        result.queuedOffline -> "Order saved locally and queued for sync."
                        session.role == "customer" -> "Order created and queued for operator review."
                        else -> "Order created successfully."
                    }
                )
            }.onFailure { throwable ->
                if (isUnauthorized(throwable)) {
                    signOut()
                } else {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, actionMessage = throwable.message ?: "Order creation failed.")
                }
            }
        }
    }

    fun transitionOrder(toStatus: String) {
        val state = uiState.value
        if (!state.isOperator) {
            _uiState.value = state.copy(actionMessage = "Only operators can change order status.")
            return
        }
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
            successMessage = { detail, queuedOffline ->
                if (queuedOffline) {
                    "Status change saved locally and queued for sync."
                } else {
                    "Order moved to ${detail.statusLabel}."
                }
            }
        )
    }

    fun addComment(body: String) {
        val state = uiState.value
        if (!state.isOperator) {
            _uiState.value = state.copy(actionMessage = "Only operators can leave queue comments.")
            return
        }
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
            successMessage = { _, queuedOffline ->
                if (queuedOffline) "Comment saved locally and queued for sync." else "Comment added successfully."
            }
        )
    }

    private fun mutateSelectedOrder(
        state: MobileHomeUiState,
        selectedOrderId: String,
        action: suspend () -> com.statusflow.mobile.data.MobileMutationResult,
        successMessage: (MobileOrderDetail, Boolean) -> String
    ) {
        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, actionMessage = null, errorMessage = null)
            runCatching {
                val result = action()
                val dashboard = repository.fetchDashboardData()
                val detail = repository.fetchOrderDetail(selectedOrderId)
                Triple(result, dashboard, detail)
            }.onSuccess { (result, dashboard, detail) ->
                _uiState.value = authenticatedState(
                    previousState = state,
                    isSubmitting = false,
                    orders = dashboard.orders,
                    users = dashboard.users,
                    syncState = dashboard.syncState,
                    allowedTransitions = dashboard.allowedTransitions,
                    selectedOrderId = selectedOrderId,
                    selectedOrderDetail = detail,
                    actionMessage = successMessage(detail, result.queuedOffline)
                )
            }.onFailure { throwable ->
                if (isUnauthorized(throwable)) {
                    signOut()
                } else {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, actionMessage = throwable.message ?: "Action failed.")
                }
            }
        }
    }

    private fun authenticatedState(
        previousState: MobileHomeUiState,
        orders: List<MobileOrderSummary>,
        users: List<MobileUserSummary>,
        syncState: MobileSyncState,
        allowedTransitions: Map<String, List<String>>,
        selectedOrderId: String?,
        selectedOrderDetail: MobileOrderDetail?,
        isSubmitting: Boolean = false,
        actionMessage: String? = null
    ): MobileHomeUiState {
        return MobileHomeUiState(
            isLoading = false,
            isRefreshing = false,
            isSubmitting = isSubmitting,
            isAuthenticating = false,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            session = previousState.session,
            orders = orders,
            users = users,
            syncState = syncState,
            allowedTransitions = allowedTransitions,
            selectedOrderId = selectedOrderId,
            selectedOrderDetail = selectedOrderDetail,
            actionMessage = actionMessage
        )
    }

    private fun isUnauthorized(throwable: Throwable): Boolean {
        return throwable is MobileSessionExpiredException
    }
}

@Composable
fun MobileHomeRoute() {
    val viewModel: MobileHomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MobileHomeViewModel(StatusFlowApiRepository.from(MobileSessionStore.appContext())) as T
    })
    val state by viewModel.uiState.collectAsState()

    MobileHomeScreen(
        state = state,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
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
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onCreateOrder: (String, String) -> Unit,
    onSelectOrder: (String) -> Unit,
    onTransitionOrder: (String) -> Unit,
    onAddComment: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var commentBody by remember { mutableStateOf("") }
    var screenModeName by rememberSaveable { mutableStateOf(MobileScreenMode.Queue.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOptionName by rememberSaveable { mutableStateOf(MobileOrderSortOption.UPDATED_DESC.name) }
    val screenMode = MobileScreenMode.valueOf(screenModeName)
    val sortOption = MobileOrderSortOption.valueOf(sortOptionName)

    // Auto-show detail when order is pre-selected via debug intent
    LaunchedEffect(state.selectedOrderDetail) {
        if (state.selectedOrderDetail != null && screenMode == MobileScreenMode.Queue) {
            screenModeName = MobileScreenMode.Detail.name
        }
    }

    val availableStatuses = state.orders.map { it.rawStatus }.distinct()

    val visibleOrders = remember(state.orders, searchQuery, statusFilter, sortOption) {
        val normalizedQuery = searchQuery.trim().lowercase()
        state.orders
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
    }

    val selectedOrderSummary = state.selectedOrderId?.let { selectedId ->
        state.orders.firstOrNull { it.id == selectedId }
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
            if (!state.isAuthenticated) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(MobileUiTags.SCROLL_CONTENT).padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item { ScreenTitle() }
                    item {
                        LoginCard(
                            email = email,
                            password = password,
                            isSubmitting = state.isAuthenticating,
                            authMessage = state.authMessage,
                            onEmailChange = { email = it },
                            onPasswordChange = { password = it },
                            onSignIn = { onSignIn(email.trim(), password) }
                        )
                    }
                    item { ApiCard(state.apiBaseUrl) }
                }
            } else {
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(MobileUiTags.SCROLL_CONTENT).padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item { ScreenTitle(session = state.session, onSignOut = onSignOut) }
                        item {
                        QueueOverviewCard(
                            totalOrders = state.orders.size,
                            visibleOrders = visibleOrders.size,
                            selectedOrderCode = state.selectedOrderDetail?.code,
                            activeFilterLabel = statusFilter?.let(::statusLabel),
                            syncState = state.syncState
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
                                when (screenMode) {
                                    MobileScreenMode.Detail -> {
                                        item {
                                            QueueSectionHeader(
                                                title = if (state.selectedOrderDetail != null) "Selected order" else "Order detail",
                                                subtitle = if (state.selectedOrderDetail != null) {
                                                    if (state.isOperator) "Review details, update status, and leave context for the next operator."
                                                    else "Review the order timeline and track the latest status from your phone."
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
                                                    onBack = { screenModeName = MobileScreenMode.Queue.name },
                                                    isOperator = state.isOperator
                                                )
                                            } else {
                                                DetailUnavailableCard(
                                                    errorMessage = state.errorMessage,
                                                    onBack = { screenModeName = MobileScreenMode.Queue.name },
                                                    onRefresh = onRefresh
                                                )
                                            }
                                        }
                                    }

                                    MobileScreenMode.Create -> {
                                        item {
                                            QueueSectionHeader(
                                                title = "New order",
                                                subtitle = "Capture the work item, then return to the queue."
                                            )
                                        }
                                        item {
                                            CreateOrderCard(
                                                title = title,
                                                description = description,
                                                isExpanded = true,
                                                isSubmitting = state.isSubmitting,
                                                isLoading = state.isLoading || state.isRefreshing,
                                                isEnabled = state.canCreateOrders,
                                                helperText = if (state.isOperator) {
                                                    "Operator mode can still capture new work into the shared queue."
                                                } else {
                                                    "Customer mode submits directly into the same shared queue."
                                                },
                                                onTitleChange = { title = it },
                                                onDescriptionChange = { description = it },
                                                onCreate = {
                                                    onCreateOrder(title.trim(), description.trim())
                                                    title = ""
                                                    description = ""
                                                    screenModeName = MobileScreenMode.Queue.name
                                                },
                                                onRefresh = onRefresh,
                                                onToggleExpanded = { screenModeName = MobileScreenMode.Queue.name }
                                            )
                                        }
                                    }

                                    MobileScreenMode.Queue -> {
                                        item {
                                            QueueSectionHeader(
                                                title = "Active queue",
                                                subtitle = "Tap a card to select it, then open details when you are ready."
                                            )
                                        }
                                        if (visibleOrders.isEmpty()) {
                                            item {
                                                EmptyQueueCard(
                                                    title = if (state.orders.isEmpty()) "No orders yet" else "No orders match your current view",
                                                    body = if (state.orders.isEmpty()) {
                                                        "Create the first order from this screen or pull down to refresh when the backend receives new work."
                                                    } else {
                                                        "Try clearing the current filter, editing the search text, or changing the search query to inspect a different slice of the queue."
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
                                                    onSelectOrder = onSelectOrder
                                                )
                                            }
                                        }
                                        if (selectedOrderSummary != null) {
                                            item {
                                                SelectedOrderTray(
                                                    order = selectedOrderSummary,
                                                    onOpen = { screenModeName = MobileScreenMode.Detail.name }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (screenMode == MobileScreenMode.Queue) {
                            item {
                                CreateOrderCard(
                                    title = title,
                                    description = description,
                                    isExpanded = false,
                                    isSubmitting = state.isSubmitting,
                                    isLoading = state.isLoading || state.isRefreshing,
                                    isEnabled = state.canCreateOrders,
                                    helperText = if (state.isOperator) {
                                        "Operator mode can still capture new work into the shared queue."
                                    } else {
                                        "Customer mode submits directly into the same shared queue."
                                    },
                                    onTitleChange = { title = it },
                                    onDescriptionChange = { description = it },
                                    onCreate = {},
                                    onRefresh = onRefresh,
                                    onToggleExpanded = { screenModeName = MobileScreenMode.Create.name }
                                )
                            }
                        }
                        item { ApiCard(state.apiBaseUrl) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScreenTitle(session: MobileSessionSummary? = null, onSignOut: (() -> Unit)? = null) {
    BoxWithConstraints(modifier = Modifier.testTag(MobileUiTags.SCREEN_TITLE)) {
        val isCompact = maxWidth < 360.dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isCompact || session == null || onSignOut == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (session?.role == "customer") "MOBILE PORTAL" else "MOBILE OPS",
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
                        if (session == null) {
                            "Sign in to reach the same live workflow used across web and mobile."
                        } else {
                            "Queue-first control for the same shared workflow used on web."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate300
                    )
                }
                if (session != null && onSignOut != null) {
                    SessionIdentityCard(session = session, onSignOut = onSignOut, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            if (session.role == "customer") "MOBILE PORTAL" else "MOBILE OPS",
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
                    SessionIdentityCard(session = session, onSignOut = onSignOut, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SessionIdentityCard(session: MobileSessionSummary, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Navy700.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Slate300.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Signed in", style = MaterialTheme.typography.labelMedium, color = Blue300)
            Text(session.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${session.role.replaceFirstChar { it.uppercase() }} | ${session.email}", style = MaterialTheme.typography.bodySmall, color = Slate300)
            Button(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy600, contentColor = Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out")
            }
        }
    }
}

@Composable
internal fun LoginCard(
    email: String,
    password: String,
    isSubmitting: Boolean,
    authMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit
) {
    ShellCard(modifier = Modifier.testTag(MobileUiTags.LOGIN_CARD)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(
                title = "Sign in to the live queue",
                subtitle = "Use the seeded operator or customer account to enter the shared workflow."
            )
            CompactInfoCard(title = "Seeded operator", value = "operator@example.com / operator123", modifier = Modifier.fillMaxWidth())
            CompactInfoCard(title = "Seeded customer", value = "customer@example.com / customer123", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.LOGIN_EMAIL_INPUT)
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.LOGIN_PASSWORD_INPUT)
            )
            if (authMessage != null) {
                FeedbackCard(
                    title = "Sign-in status",
                    body = authMessage,
                    accent = if (authMessage.contains("expired", ignoreCase = true) || authMessage.contains("signed out", ignoreCase = true)) Amber300 else Red300,
                    surface = Navy700,
                    eyebrow = "AUTH"
                )
            }
            Button(
                enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
                onClick = onSignIn,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue400,
                    contentColor = Navy900,
                    disabledContainerColor = Navy600,
                    disabledContentColor = Slate300
                ),
                modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.LOGIN_SUBMIT)
            ) {
                Text(if (isSubmitting) "Signing in..." else "Sign in")
            }
        }
    }
}

@Composable
private fun ApiCard(apiBaseUrl: String) {
    ShellCard(modifier = Modifier.testTag(MobileUiTags.CREATE_CARD)) {
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Connected backend", style = MaterialTheme.typography.labelMedium, color = Blue300)
                        Text(apiBaseUrl, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                    Text(
                        "Live",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mint400,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
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
    }
}

@Composable
internal fun QueueOverviewCard(
    totalOrders: Int,
    visibleOrders: Int,
    selectedOrderCode: String?,
    activeFilterLabel: String?,
    syncState: MobileSyncState = MobileSyncState()
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.QUEUE_OVERVIEW),
        colors = CardDefaults.cardColors(containerColor = Navy500),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Blue300.copy(alpha = 0.3f))
    ) {
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Queue snapshot", style = MaterialTheme.typography.labelLarge, color = Mint400, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            syncState.isUsingCachedData && syncState.lastSuccessfulRefreshLabel != null ->
                                "Cached view from ${syncState.lastSuccessfulRefreshLabel}"
                            syncState.isUsingCachedData -> "Cached view ready while network is unavailable."
                            syncState.lastSuccessfulRefreshLabel != null ->
                                "Last sync ${syncState.lastSuccessfulRefreshLabel}"
                            else -> "Active workload first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate100
                    )
                }
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile(
                            label = "Total",
                            value = totalOrders.toString(),
                            accent = Blue400,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MetricTile(
                            label = "Visible",
                            value = visibleOrders.toString(),
                            accent = Mint400,
                            modifier = Modifier.fillMaxWidth()
                        )
                        CompactInfoCard(
                            title = "Focus",
                            value = selectedOrderCode ?: "None",
                            modifier = Modifier.fillMaxWidth()
                        )
                        CompactInfoCard(
                            title = "Filter",
                            value = activeFilterLabel ?: "All",
                            modifier = Modifier.fillMaxWidth()
                        )
                        CompactInfoCard(
                            title = "Pending sync",
                            value = syncState.pendingMutationCount.toString(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
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
                        CompactInfoCard(
                            title = "Pending sync",
                            value = syncState.pendingMutationCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateOrderCard(
    title: String,
    description: String,
    isExpanded: Boolean,
    isSubmitting: Boolean,
    isLoading: Boolean,
    isEnabled: Boolean,
    helperText: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    ShellCard(modifier = Modifier.testTag(MobileUiTags.LIST_CONTROLS)) {
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Create order", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isExpanded) helperText else "Open a compact composer when you need to add work.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate300
                            )
                        }
                        Button(
                            enabled = !isSubmitting && isEnabled,
                            onClick = onToggleExpanded,
                            modifier = Modifier
                                .testTag(MobileUiTags.CREATE_TOGGLE)
                                .fillMaxWidth()
                                .semantics {
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text("Create order", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isExpanded) helperText else "Open a compact composer when you need to add work.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate300
                            )
                        }
                        Button(
                            enabled = !isSubmitting && isEnabled,
                            onClick = onToggleExpanded,
                            modifier = Modifier
                                .testTag(MobileUiTags.CREATE_TOGGLE)
                                .semantics {
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
                }

                if (isExpanded) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        label = { Text("Order title") },
                        modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.CREATE_TITLE_INPUT).semantics {
                            contentDescription = "Order title"
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Operator brief") },
                        modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.CREATE_DESCRIPTION_INPUT).semantics {
                            contentDescription = "Operator brief"
                        },
                        minLines = 3
                    )
                    if (isCompact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                enabled = !isSubmitting && isEnabled && title.length >= 3,
                                onClick = onCreate,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue400,
                                    contentColor = Navy900,
                                    disabledContainerColor = Navy600,
                                    disabledContentColor = Slate300
                                ),
                                modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.CREATE_SUBMIT)
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Refresh")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                enabled = !isSubmitting && isEnabled && title.length >= 3,
                                onClick = onCreate,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue400,
                                    contentColor = Navy900,
                                    disabledContainerColor = Navy600,
                                    disabledContentColor = Slate300
                                ),
                                modifier = Modifier.weight(1f).testTag(MobileUiTags.CREATE_SUBMIT)
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
                if (!isEnabled) {
                    FeedbackInline(label = "Sign in before creating a new order", accent = Amber300)
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
internal fun ListControlsCard(
    searchQuery: String,
    selectedStatus: String?,
    availableStatuses: List<String>,
    sortOption: MobileOrderSortOption,
    onSearchQueryChange: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onToggleSort: () -> Unit
) {
    ShellCard {
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(title = "Queue controls", subtitle = "Slice the queue fast when volume rises.")
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search code, title, or customer") },
                    modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.SEARCH_INPUT),
                    singleLine = true
                )
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sort", style = MaterialTheme.typography.labelSmall, color = Slate300)
                        Text(sortOptionLabel(sortOption), style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort", style = MaterialTheme.typography.labelSmall, color = Slate300)
                        Text(sortOptionLabel(sortOption), style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (selectedStatus != null && isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton(
                            label = "Sort",
                            onClick = onToggleSort,
                            accent = Blue400,
                            stateDescription = sortOptionLabel(sortOption),
                            modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.SORT_BUTTON)
                        )
                        PillButton(
                            label = "Clear filter",
                            onClick = { onSelectStatus(selectedStatus) },
                            accent = Slate200,
                            stateDescription = "Status filter active",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton(
                            label = "Sort",
                            onClick = onToggleSort,
                            accent = Blue400,
                            stateDescription = sortOptionLabel(sortOption),
                            modifier = Modifier.weight(1f).testTag(MobileUiTags.SORT_BUTTON)
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
                }
                if (availableStatuses.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status filter", style = MaterialTheme.typography.labelSmall, color = Slate300)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                            items(availableStatuses) { status ->
                                val active = selectedStatus == status
                                Button(
                                    onClick = { onSelectStatus(status) },
                                    modifier = Modifier
                                        .testTag(MobileUiTags.statusChip(status))
                                        .semantics {
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
}

@Composable
internal fun DetailScreenCard(
    detail: MobileOrderDetail,
    allowedTransitions: List<String>,
    isSubmitting: Boolean,
    actionMessage: String?,
    commentBody: String,
    onCommentBodyChange: (String) -> Unit,
    onTransitionOrder: (String) -> Unit,
    onAddComment: () -> Unit,
    onBack: () -> Unit,
    isOperator: Boolean
) {
    BoxWithConstraints {
        val isCompact = maxWidth < 360.dp
        Column(modifier = Modifier.testTag(MobileUiTags.DETAIL_SCREEN), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    if (isCompact) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(label = detail.statusLabel, accent = detail.statusColor)
                            Text("Updated ${detail.updatedAtLabel}", style = MaterialTheme.typography.bodySmall, color = Slate200)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            CompactInfoCard(
                                title = "Customer",
                                value = detail.customerName,
                                modifier = Modifier.fillMaxWidth()
                            )
                            CompactInfoCard(
                                title = "Comments",
                                value = detail.comments.size.toString(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
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
                if (!isOperator) {
                    Text("Customer mode is read-only for status changes.", style = MaterialTheme.typography.bodyMedium, color = Slate200)
                } else if (allowedTransitions.isEmpty()) {
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

            DetailSectionCard(title = "Comments", subtitle = if (isOperator) "Leave a note for the next operator." else "Comments are available in operator mode.") {
                if (isOperator) {
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
                        modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.COMMENT_SUBMIT)
                    ) {
                        Text(if (isSubmitting) "Sending..." else "Post comment")
                    }
                    OutlinedTextField(
                        value = commentBody,
                        onValueChange = onCommentBodyChange,
                        label = { Text("Add operator note") },
                        modifier = Modifier.fillMaxWidth().testTag(MobileUiTags.COMMENT_INPUT).semantics {
                            contentDescription = "Add operator note"
                        },
                        minLines = 2
                    )
                } else {
                    FeedbackInline(label = "Sign in as an operator to leave queue notes", accent = Amber300)
                }
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
}

@Composable
internal fun DetailUnavailableCard(errorMessage: String?, onBack: () -> Unit, onRefresh: () -> Unit) {
    ShellCard(modifier = Modifier.testTag(MobileUiTags.DETAIL_UNAVAILABLE)) {
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
internal fun EmptyQueueCard(title: String, body: String, eyebrow: String, accent: Color) {
    ShellCard(modifier = Modifier.testTag(MobileUiTags.EMPTY_QUEUE)) {
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Slate200)
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactInfoCard(
                            title = "Next move",
                            value = if (eyebrow == "EMPTY QUEUE") "Create order" else "Clear filters",
                            modifier = Modifier.fillMaxWidth()
                        )
                        CompactInfoCard(
                            title = "Mode",
                            value = if (eyebrow == "EMPTY QUEUE") "Fresh start" else "Narrow slice",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
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
    }
}

@Composable
internal fun SelectedOrderTray(order: MobileOrderSummary, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Navy900.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Blue300.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${order.code} selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Open details, history, and comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate300,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Slate100)
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
internal fun OrderCard(order: MobileOrderSummary, isSelected: Boolean, onSelectOrder: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MobileUiTags.orderCard(order.code))
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
        BoxWithConstraints {
            val isCompact = maxWidth < 360.dp
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(order.code, style = MaterialTheme.typography.labelLarge, color = Blue300, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isSelected) "Ready in focus lane" else "Tap to open details",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Mint400 else Slate300
                            )
                        }
                        StatusBadge(label = order.statusLabel, accent = order.statusColor)
                    }
                } else {
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
                }
                Text(
                    order.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactMetaPill(
                            label = "Customer",
                            value = order.customerName,
                            modifier = Modifier.fillMaxWidth()
                        )
                        CompactMetaPill(
                            label = "Updated",
                            value = compactTimestampLabel(order.updatedAtLabel),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
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
                }
                if (isSelected) {
                    FeedbackInline(label = "Selected for detail view", accent = Mint400)
                }
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
private fun ShellCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
