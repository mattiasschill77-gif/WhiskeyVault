package com.schill.whiskeyvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig // VIKTIG NY IMPORT!
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
            safetySettings = safetySettings,
            // MASTER-TIPS: Tvingar modellen att agera analytiskt och svara i JSON
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.2f // Lägre temperatur = mindre gissningar, mer fakta
            }
        )
    } else null

    suspend fun analyzeWhiskey(file: File): Whiskey? {
        val currentModel = model ?: return null

        return try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            // Skala ner bilden för prestanda och minne (Masterkod-standard)
            val maxDimension = 1024f
            val scale = maxDimension / maxOf(originalBitmap.width, originalBitmap.height)
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * scale).toInt(),
                    (originalBitmap.height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            // --- HÄR ÄR DEN NYA GATEKEEPER-PROMPTEN ---
            val inputContent = content {
                image(scaledBitmap)
                text("""
                    You are a world-class whiskey and spirits expert. 
                    STEP 1: Analyze the image. Does it clearly contain an alcoholic beverage, a liquor bottle, or a spirits label?
                    If NO: Return EXACTLY this JSON and nothing else: {"error": "not_alcohol"}
                    
                    If YES (STEP 2): Identify the bottle and return ONLY a JSON object using these exact keys:
                    "name": Full name and age,
                    "country": Country of origin,
                    "region": Specific region,
                    "price": Estimated price in SEK,
                    "abv": Alcohol percentage,
                    "type": Style,
                    "volume": Size,
                    "flavors": Pick 3 notes (Vanilla, Caramel, etc.),
                    "stores": "List 2-3 major retailers in the user's region (default to Sweden/Systembolaget) that sell this."
                """.trimIndent())
            }

            val response = currentModel.generateContent(inputContent)
            val rawText = response.text ?: ""

            val startIndex = rawText.indexOf("{")
            val endIndex = rawText.lastIndexOf("}")

            if (startIndex != -1 && endIndex != -1) {
                val cleanJson = rawText.substring(startIndex, endIndex + 1)
                val json = JSONObject(cleanJson)

                // --- HÄR FÅNGAR VI UPP OCH STOPPAR KATTEN/KAFFEKOPPEN ---
                if (json.has("error")) {
                    android.util.Log.w("AiHelper", "AI avbröt: Bilden innehåller ingen alkohol.")
                    return null
                }

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
            android.util.Log.e("AiHelper", "Error under AI-analys: ${e.localizedMessage}")
            null
        }
    }
}