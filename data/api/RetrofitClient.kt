package com.example.modrinthforandroid.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// RetrofitClient is a singleton — it only ever creates ONE instance of itself.
// This is important so we don't waste memory spinning up multiple HTTP clients.
object RetrofitClient {

    private const val BASE_URL = "https://api.modrinth.com/v2/"

    // Modrinth requires a User-Agent header identifying your app.
    // Replace "YourName" with your name or GitHub username.
    private const val USER_AGENT = "ModrinthAndroidApp/1.0 (YourName)"

    // OkHttp is the HTTP library underneath Retrofit.
    // We add an interceptor that adds the User-Agent header to every request automatically.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(
            // This logs all HTTP requests/responses to Logcat — super useful for debugging!
            // In a production app you'd disable this.
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    // The actual Retrofit instance
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create()) // Converts JSON to Kotlin data classes
        .build()

    // The API service — this is what you'll call throughout the app
    val apiService: ModrinthApiService = retrofit.create(ModrinthApiService::class.java)
}