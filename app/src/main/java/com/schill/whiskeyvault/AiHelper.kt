package com.schill.whiskeyvault

import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject
import java.io.File

class AiHelper(private val apiKey: String) {

    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
    )

    private val model: GenerativeModel? = if (apiKey.isNotEmpty()) {
        GenerativeModel(
            modelName = "gemini-3-flash-preview",
            apiKey = apiKey,
            safetySettings = safetySettings
        )
    } else null

    suspend fun analyzeWhiskey(file: File): Whiskey? {
        val currentModel = model ?: return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            val inputContent = content {
                image(bitmap)
                text("""
                    You are a world-class whiskey expert. Identify the bottle in this image.
                    Return ONLY a JSON object.
                    
                    Use these exact keys:
                    "name": Full name and age,
                    "country": Country of origin,
                    "region": Specific region,
                    "price": Estimated price in SEK,
                    "abv": Alcohol percentage,
                    "type": Style,
                    "volume": Size,
                    "flavors": Pick 3 notes (Vanilla, Caramel, etc.),
                    "stores": "List 2-3 major retailers in the user's region (default to Sweden/Systembolaget) that sell this."
                    
                    Important: No markdown, only raw JSON.
                """.trimIndent())
            }

            val response = currentModel.generateContent(inputContent)
            val rawText = response.text ?: ""

            val startIndex = rawText.indexOf("{")
            val endIndex = rawText.lastIndexOf("}")

            if (startIndex != -1 && endIndex != -1) {
                val cleanJson = rawText.substring(startIndex, endIndex + 1)
                val json = JSONObject(cleanJson)

                val country = json.optString("country", "Unknown")
                val region = json.optString("region", "")
                val origin = if (region.isNotBlank() && region != "null") "$country, $region" else country

                val rawFlavors = json.optString("flavors", "")
                val cleanFlavors = rawFlavors.replace("[", "").replace("]", "").replace("\"", "").replace(";", ",")

                return Whiskey(
                    id = 0,
                    name = json.optString("name", "Unknown Whiskey"),
                    country = origin,
                    price = json.optString("price", "0"),
                    abv = json.optString("abv", ""),
                    type = json.optString("type", "Unknown"),
                    volume = json.optString("volume", "70cl"),
                    flavorProfile = cleanFlavors,
                    rating = 5,
                    imageUrl = null,
                    notes = "Suggested stores: ${json.optString("stores", "Systembolaget")}"
                )
            } else null
        } catch (e: Exception) {
            Log.e("AiHelper", "Error: ${e.localizedMessage}")
            null
        }
    }
}