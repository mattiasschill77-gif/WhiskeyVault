package com.schill.whiskeyvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class AiHelper {

    // 🔗 KLISTRA IN DIN URL FRÅN TERMINALEN HÄR!
    private val firebaseFunctionUrl = " https://us-central1-whiskeyvault-ai.cloudfunctions.net/identifyWhiskey"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeWhiskey(file: File): Whiskey? = withContext(Dispatchers.IO) {
        try {
            // 1. Läs in och skala bilden (samma som förut, bra för prestanda!)
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val scaledBitmap = scaleBitmap(originalBitmap)

            // 2. Konvertera bilden till Base64-sträng
            val byteArrayOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 3. Skapa JSON-paketet till Firebase
            val jsonRequest = JSONObject().apply {
                put("image", base64Image)
            }

            val request = Request.Builder()
                .url(firebaseFunctionUrl)
                .post(jsonRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // 4. Skicka till din Firebase Function
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e("AiHelper", "Serverfel (${response.code}): $responseBody")
                    return@withContext null
                }

                // 5. Tolka svaret (Gemini returnerar nu data via din server)
                parseWhiskeyResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e("AiHelper", "Error under Firebase-analys: ${e.localizedMessage}")
            null
        }
    }

    private fun scaleBitmap(source: Bitmap): Bitmap {
        val maxDimension = 1024f
        val scale = maxDimension / maxOf(source.width, source.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt(),
                (source.height * scale).toInt(),
                true
            )
        } else source
    }

    private fun parseWhiskeyResponse(jsonString: String): Whiskey? {
        return try {
            // Logga för säkerhets skull så du ser exakt vad som kommer in i Android!
            Log.d("AiHelper", "Svar från molnet: $jsonString")

            // Nu skickar din server RENT data, vi behöver inte gräva i "candidates" längre!
            val json = JSONObject(jsonString)

            // Om AI:n svarade {"error": "not_alcohol"}
            if (json.has("error")) {
                Log.w("AiHelper", "AI avvisade bilden som icke-alkohol.")
                return null
            }

            Whiskey(
                id = 0,
                name = json.optString("name", "Unknown Whiskey"),
                country = json.optString("country", "Unknown"),
                price = json.optString("price", "0"),
                abv = "", // Vi hämtar inte ABV just nu
                type = json.optString("type", "Unknown"),
                volume = "70cl",
                flavorProfile = json.optString("flavorProfile", ""),
                rating = 5,
                imageUrl = null,
                notes = "Distillery: ${json.optString("distillery", "Unknown")}"
            )
        } catch (e: Exception) {
            Log.e("AiHelper", "Android kunde inte läsa JSON: ${e.localizedMessage}")
            null
        }
    }
}