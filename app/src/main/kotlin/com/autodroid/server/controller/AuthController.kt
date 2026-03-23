package com.autodroid.server.controller

import com.autodroid.server.HttpServer

/**
 * Registers authentication management routes.
 *
 * @param rotateTokens callback that regenerates both tokens, persists them,
 *                     updates the AuthMiddleware, and returns (fullToken, readToken).
 */
fun registerAuthRoutes(
    server: HttpServer,
    rotateTokens: () -> Pair<String, String>,
) {

    // POST /api/auth/rotate-tokens
    // Requires FULL token (enforced by AuthMiddleware — this is a POST endpoint).
    // Regenerates both FULL and READ tokens. The caller MUST save the new tokens
    // immediately; the old tokens are invalidated upon return.
    server.post("/api/auth/rotate-tokens") { _, res ->
        val (fullToken, readToken) = rotateTokens()
        // Prevent proxies/browsers from caching the token response
        res.header("Cache-Control", "no-store")
        res.sendJson(mapOf(
            "fullToken" to fullToken,
            "readToken" to readToken,
            "message" to "Tokens rotated successfully. Old tokens are now invalid.",
            "warning" to "Store tokens securely. This endpoint should only be called via trusted network or adb forward.",
        ))
    }
}
