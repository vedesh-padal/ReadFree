package com.vedesh.readfree.data.repository

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RaindropRepository(private val settingsRepo: SettingsRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun syncArticle(url: String, title: String) {
        if (!settingsRepo.isRaindropSyncEnabled()) return
        val token = settingsRepo.getRaindropToken() ?: return

        val json = JSONObject().apply {
            put("link", url)
            put("title", title)
            put("pleaseParse", JSONObject()) // Tells Raindrop to parse article
        }

        val request = Request.Builder()
            .url("https://api.raindrop.io/rest/v1/raindrop")
            .post(json.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("RaindropRepository", "Failed to sync to Raindrop", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("RaindropRepository", "Unexpected code $response")
                    } else {
                        Log.d("RaindropRepository", "Successfully synced to Raindrop")
                    }
                }
            }
        })
    }
}
