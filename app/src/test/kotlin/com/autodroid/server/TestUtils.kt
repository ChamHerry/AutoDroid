package com.autodroid.server

import java.io.ByteArrayOutputStream

fun fakeRequest(
    method: String = "GET",
    path: String = "/",
    body: String = "",
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
) = Request(method = method, path = path, headers = headers, query = query, params = emptyMap(), body = body)

fun fakeResponse(): Pair<Response, ByteArrayOutputStream> {
    val out = ByteArrayOutputStream()
    return Response(out) to out
}
