package com.statusflow.mobile.data

import com.statusflow.mobile.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class OrderApiResponse(
    val id: String,
    val code: String,
    val title: String,
    val customer_name: String,
    val status: String,
    val updated_at: String
)

interface OrderApiService {
    @GET("orders")
    suspend fun listOrders(): List<OrderApiResponse>
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
