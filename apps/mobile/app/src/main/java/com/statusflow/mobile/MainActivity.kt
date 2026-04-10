package com.statusflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
    val isSubmitting: Boolean = false,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val orders: List<MobileOrderSummary> = emptyList(),
    val users: List<MobileUserSummary> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

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
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                actionMessage = null
            )

            runCatching { repository.fetchDashboardData() }
                .onSuccess { dashboard ->
                    _uiState.value = MobileHomeUiState(
                        isLoading = false,
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        orders = dashboard.orders,
                        users = dashboard.users
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = MobileHomeUiState(
                        isLoading = false,
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        orders = emptyList(),
                        users = emptyList(),
                        errorMessage = throwable.message ?: "Unknown network error."
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
                repository.fetchDashboardData()
            }.onSuccess { dashboard ->
                _uiState.value = MobileHomeUiState(
                    isLoading = false,
                    isSubmitting = false,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    orders = dashboard.orders,
                    users = dashboard.users,
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
        onCreateOrder = viewModel::createOrder
    )
}

@Composable
fun MobileHomeScreen(
    state: MobileHomeUiState,
    onRefresh: () -> Unit,
    onCreateOrder: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "StatusFlow",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mobile client connected to the shared API. Create new orders here, then watch them show up in the operator dashboard.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD2DAE6)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172A45))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Current API",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF92B1F7)
                        )
                        Text(
                            text = state.apiBaseUrl,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }

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
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                enabled = !state.isSubmitting && title.length >= 3,
                                onClick = {
                                    onCreateOrder(title.trim(), description.trim())
                                    title = ""
                                    description = ""
                                }
                            ) {
                                Text(if (state.isSubmitting) "Submitting..." else "Create")
                            }
                            Button(
                                enabled = !state.isLoading && !state.isSubmitting,
                                onClick = onRefresh
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                }

                if (state.actionMessage != null) {
                    StatusMessageCard(
                        title = "Latest action",
                        body = state.actionMessage
                    )
                }

                when {
                    state.isLoading -> {
                        StatusMessageCard(
                            title = "Syncing orders",
                            body = "Fetching the latest order list from the API."
                        )
                    }

                    state.errorMessage != null -> {
                        StatusMessageCard(
                            title = "Sync failed",
                            body = state.errorMessage
                        )
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(state.orders) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF172A45)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.code,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF92B1F7)
                                        )
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Customer: ${item.customerName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFD2DAE6)
                                        )
                                        Text(
                                            text = "Status: ${item.statusLabel}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = item.statusColor
                                        )
                                        Text(
                                            text = "Updated ${item.updatedAtLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF92B1F7)
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
