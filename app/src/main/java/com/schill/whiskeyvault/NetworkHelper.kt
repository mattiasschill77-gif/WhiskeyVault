package com.schill.whiskeyvault

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

object NetworkHelper {
    private val client = OkHttpClient()

    fun uploadToImgur(file: File, onComplete: (String?) -> Unit) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/*".toMediaTypeOrNull())).build()

        val req = Request.Builder()
            .url("https://api.imgur.com/3/image")
            .header("Authorization", "Client-ID 546c25a59c58ad7") // Din Imgur Client-ID
            .post(body).build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                try {
                    val link = JSONObject(res ?: "").getJSONObject("data").getString("link")
                    onComplete(link)
                } catch (e: Exception) {
                    onComplete(null)
                }
            }
        })
    }
}