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

    // Säkerhetsinställningar för att tillåta bilder på alkohol/flaskor
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
    )

    // VIKTIGT 2026: Vi använder gemini-3-flash-preview då 1.5 har stängts ner
    private val model: GenerativeModel? = if (apiKey.isNotEmpty()) {
        GenerativeModel(
            modelName = "gemini-3-flash-preview",
            apiKey = apiKey,
            safetySettings = safetySettings
        )
    } else {
        null
    }

    suspend fun analyzeWhiskey(file: File): Whiskey? {
        val currentModel = model ?: run {
            Log.e("AiHelper", "Modellen kunde inte skapas. Kontrollera API-nyckeln.")
            return null
        }

        return try {
            Log.d("AiHelper", "Avkodar bildfil: ${file.absolutePath}")
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            if (bitmap == null) {
                Log.e("AiHelper", "Kunde inte avkoda bilden.")
                return null
            }

            val inputContent = content {
                image(bitmap)
                text("""
                    You are a whiskey expert. Identify this bottle from the image.
                    Return ONLY a JSON object with these exact keys:
                    "name": brand and specific expression name,
                    "country": origin country,
                    "price": typical price in SEK,
                    "abv": alcohol percentage,
                    "type": style (e.g., Single Malt, Bourbon, Blended),
                    "volume": bottle size (e.g., 70cl),
                    "flavors": 3 main tasting notes separated by comma.
                    
                    IMPORTANT: 
                    1. Return all values as Strings (text within quotes).
                    2. Do not include markdown like ```json.
                    3. Only return the raw JSON braces { }.
                """.trimIndent())
            }

            Log.d("AiHelper", "Anropar Gemini 3 med nyckel: ${apiKey.take(5)}...")
            val response = currentModel.generateContent(inputContent)
            val rawText = response.text ?: ""

            Log.d("AiHelper", "Raw AI Response: $rawText")

            if (rawText.isBlank()) {
                Log.e("AiHelper", "AI svarade med en tom sträng.")
                return null
            }

            // JSON-TVÄTT: Hittar innehållet mellan måsvingarna
            val startIndex = rawText.indexOf("{")
            val endIndex = rawText.lastIndexOf("}")

            if (startIndex != -1 && endIndex != -1) {
                val cleanJson = rawText.substring(startIndex, endIndex + 1)
                val json = JSONObject(cleanJson)

                Log.d("AiHelper", "Analys lyckades för: ${json.optString("name")}")

                return Whiskey(
                    id = 0,
                    name = json.optString("name", "Unknown Whiskey"),
                    country = json.optString("country", "Unknown"),
                    price = json.optString("price", ""),
                    abv = json.optString("abv", ""),
                    type = json.optString("type", "Unknown"),
                    volume = json.optString("volume", "70cl"),
                    flavorProfile = json.optString("flavors", ""),
                    rating = 5,
                    imageUrl = null,
                    notes = "AI Generated identification."
                )
            } else {
                Log.e("AiHelper", "Kunde inte hitta JSON-data i svaret.")
                null
            }
        } catch (e: Exception) {
            Log.e("AiHelper", "KRASCH i analyzeWhiskey: ${e.localizedMessage}")
            null
        }
    }
}