package com.example.clatproject

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object EmailSender {

    private val client = OkHttpClient()

    // 🔐 API Key removed as requested
    private const val API_KEY = "REMOVED"

    // ✅ Domain changed to vitstudent.ac.in
    private const val FROM_EMAIL = "admin@vitstudent.ac.in"

    fun sendOtp(targetEmail: String, otp: String, onResult: (Boolean, String?) -> Unit) {
        if (API_KEY == "REMOVED") {
            onResult(false, "API Key missing")
            return
        }

        val json = JSONObject().apply {
            put("personalizations", JSONArray().put(
                JSONObject().apply {
                    put("to", JSONArray().put(
                        JSONObject().put("email", targetEmail)
                    ))
                }
            ))

            put("from", JSONObject().put("email", FROM_EMAIL))
            put("subject", "OTP Verification")
            put("content", JSONArray().put(
                JSONObject().apply {
                    put("type", "text/html")
                    put("value", "<html><body><h2>Your OTP is: <b>$otp</b></h2></body></html>")
                }
            ))
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.sendgrid.com/v3/mail/send")
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, "Connection Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                if (response.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, "Error ${response.code}: $resBody")
                }
            }
        })
    }
}
