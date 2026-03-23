package com.autodroid.server

import org.json.JSONObject

data class Request(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val query: Map<String, String>,
    val params: Map<String, String>,
    val body: String,
    val remoteIp: String = "unknown",
) {
    val jsonBody: JSONObject by lazy {
        try {
            if (body.isNotBlank()) JSONObject(body) else JSONObject()
        } catch (e: org.json.JSONException) {
            throw ApiException(400, "Invalid JSON body: ${e.message}")
        }
    }
}
