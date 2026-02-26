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
    } else {
        null
    }

    suspend fun analyzeWhiskey(file: File): Whiskey? {
        val currentModel = model ?: return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            val inputContent = content {
                image(bitmap)
                text("""
                    You are a world-class whiskey expert. Identify the bottle in this image.
                    Return ONLY a JSON object. Be precise with names (e.g., "Lagavulin 16 Year Old").
                    
                    Use these exact keys:
                    "name": Full name and age,
                    "country": Country of origin,
                    "region": Specific region (e.g. Islay, Speyside, Kentucky),
                    "price": Estimated price in SEK (numbers only),
                    "abv": Alcohol percentage (e.g. 43),
                    "type": Style (Single Malt, Bourbon, etc.),
                    "volume": Size (e.g. 70cl),
                    "flavors": Pick the 3 most prominent notes from this list: Vanilla, Caramel, Honey, Smoke, Peat, Oak, Apple, Cinnamon, Dark Chocolate.
                    
                    Important: No markdown, only raw JSON. If unsure about a value, provide your best expert guess.
                """.trimIndent())
            }

            val response = currentModel.generateContent(inputContent)
            val rawText = response.text ?: ""

            // JSON-tvätt
            val startIndex = rawText.indexOf("{")
            val endIndex = rawText.lastIndexOf("}")

            if (startIndex != -1 && endIndex != -1) {
                val cleanJson = rawText.substring(startIndex, endIndex + 1)
                val json = JSONObject(cleanJson)

                // Vi kombinerar land och region för en fylligare beskrivning
                val origin = "${json.optString("country")}${if(json.has("region")) ", " + json.optString("region") else ""}"
                val rawFlavors = json.optString("flavors", "")
                val cleanFlavors = rawFlavors
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .replace(";", ",") // Om AI:n använde semikolon

                return Whiskey(
                    id = 0,
                    name = json.optString("name", "Unknown Whiskey"),
                    country = origin,
                    price = json.optString("price", "0"),
                    abv = json.optString("abv", ""),
                    type = json.optString("type", "Unknown"),
                    volume = json.optString("volume", "70cl"),
                    flavorProfile = cleanFlavors, // <--- Här använder vi den tvättade texten
                    rating = 5,
                    imageUrl = null,
                    notes = "Expert AI Analysis completed."
                )
            } else null
        } catch (e: Exception) {
            Log.e("AiHelper", "Error: ${e.localizedMessage}")
            null
        }
    }
}
