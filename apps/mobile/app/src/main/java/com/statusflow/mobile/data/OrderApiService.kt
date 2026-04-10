package com.statusflow.mobile.data

import com.statusflow.mobile.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path

data class OrderApiResponse(
    val id: String,
    val code: String,
    val title: String,
    val customer_name: String,
    val status: String,
    val updated_at: String
)

data class UserApiResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: String
)

data class OrderCommentAuthorApiResponse(
    val id: String,
    val name: String,
    val role: String
)

data class OrderCommentApiResponse(
    val id: String,
    val body: String,
    val created_at: String,
    val author: OrderCommentAuthorApiResponse
)

data class OrderHistoryActorApiResponse(
    val id: String,
    val name: String,
    val role: String
)

data class OrderHistoryApiResponse(
    val id: String,
    val from_status: String?,
    val to_status: String,
    val reason: String,
    val changed_at: String,
    val changed_by: OrderHistoryActorApiResponse
)

data class OrderDetailApiResponse(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val customer_name: String,
    val status: String,
    val updated_at: String,
    val comments: List<OrderCommentApiResponse>,
    val history: List<OrderHistoryApiResponse>
)

data class OrderStatusLifecycleApiResponse(
    val statuses: List<String>,
    val allowed_transitions: Map<String, List<String>>
)

data class CreateOrderRequest(
    val title: String,
    val description: String,
    val customer_id: String
)

data class TransitionOrderStatusRequest(
    val changed_by_id: String,
    val to_status: String,
    val reason: String
)

interface OrderApiService {
    @GET("orders")
    suspend fun listOrders(): List<OrderApiResponse>

    @GET("orders/{orderId}")
    suspend fun getOrder(@Path("orderId") orderId: String): OrderDetailApiResponse

    @GET("users")
    suspend fun listUsers(): List<UserApiResponse>

    @GET("order-status-lifecycle")
    suspend fun getOrderStatusLifecycle(): OrderStatusLifecycleApiResponse

    @POST("orders")
    suspend fun createOrder(@Body payload: CreateOrderRequest): OrderDetailApiResponse

    @POST("orders/{orderId}/status-transitions")
    suspend fun transitionOrderStatus(
        @Path("orderId") orderId: String,
        @Body payload: TransitionOrderStatusRequest
    ): OrderDetailApiResponse
}

object OrderApiClient {
    private val loggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val service: OrderApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OrderApiService::class.java)
}
