package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "role") val role: String,
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class SystemInstruction(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: SystemInstruction? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun resolveApiKey(): String {
        return listOf(
            runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault(""),
            runCatching { BuildConfig.GEMINI_KEY }.getOrDefault("")
        ).firstOrNull { key ->
            key.isNotBlank() && key != "MY_GEMINI_KEY" && key != "MY_GEMINI_API_KEY"
        } ?: ""
    }

    /**
     * Android voice calls use text generation + on-device TTS (not the web Live WebSocket stream).
     */
    suspend fun generatePersonaResponse(
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): String {
        val apiKey = resolveApiKey()
        if (apiKey.isEmpty()) {
            return "Gemini API key is missing. Add GEMINI_API_KEY to the project's .env file and rebuild the app."
        }

        val contentsList = history.mapNotNull { (sender, text) ->
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val role = when (sender.lowercase()) {
                "user" -> "user"
                "model", "bot", "assistant" -> "model"
                else -> "user"
            }
            Content(role = role, parts = listOf(Part(text = trimmed)))
        }

        if (contentsList.isEmpty()) {
            return "I didn't catch that. Could you say it again?"
        }

        val request = GenerateContentRequest(
            contents = contentsList,
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "I apologize, I was unable to form a response. Could you repeat that?"
        } catch (e: Throwable) {
            e.printStackTrace()
            "Connection error: ${e.message ?: "unknown"}. Check your network and API key."
        }
    }
}
