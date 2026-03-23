package com.autodroid.server

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class TokenScope { READ, FULL }

class AuthMiddleware(tokens: Map<TokenScope, String>) : Middleware {

    /** Convenience constructor for single full-access token (backward compatible). */
    constructor(fullToken: String) : this(mapOf(TokenScope.FULL to fullToken))

    private val publicPaths = setOf("/api/status")
    @Volatile
    private var tokenBytesMap: Map<TokenScope, ByteArray> =
        tokens.mapValues { it.value.toByteArray(Charsets.UTF_8) }

    /**
     * Atomically replace the active tokens. All subsequent requests will be
     * validated against the new tokens; previously issued tokens become invalid
     * immediately.
     */
    fun updateTokens(newTokens: Map<TokenScope, String>) {
        tokenBytesMap = newTokens.mapValues { it.value.toByteArray(Charsets.UTF_8) }
    }

    // Rate limiting: track consecutive failures per source IP
    private val failureCounts = ConcurrentHashMap<String, FailureRecord>()

    private data class FailureRecord(val count: Int, val lastFailure: Long)

    override suspend fun handle(request: Request, response: Response, next: suspend () -> Unit) {
        // Non-API requests (static files) and public endpoints bypass auth
        if (!request.path.startsWith("/api/") || request.path in publicPaths) {
            next()
            return
        }

        val clientIp = request.remoteIp

        // Check rate limit before processing
        val record = failureCounts[clientIp]
        if (record != null && record.count >= MAX_FAILURES) {
            val backoffMs = minOf(BASE_BACKOFF_MS shl (record.count - MAX_FAILURES).coerceAtMost(5), MAX_BACKOFF_MS)
            if (System.currentTimeMillis() - record.lastFailure < backoffMs) {
                response.sendError(429, "Too many authentication failures. Try again later.")
                return
            }
        }

        val inputToken = extractToken(request)
        if (inputToken == null) {
            recordFailure(clientIp)
            response.sendError(
                401,
                "Authentication required. Use 'Authorization: Bearer <token>' header or '?token=<token>' query parameter.",
            )
            return
        }

        val scope = resolveScope(inputToken)
        if (scope == null) {
            recordFailure(clientIp)
            response.sendError(403, "Invalid token")
            return
        }

        // Auth succeeded — clear failure record
        failureCounts.remove(clientIp)

        // Read-only tokens can only access GET endpoints
        if (scope == TokenScope.READ && requiresFullScope(request)) {
            response.sendError(403, "Insufficient permissions: read-only token cannot perform write operations")
            return
        }

        next()
    }

    private fun recordFailure(clientIp: String) {
        failureCounts.compute(clientIp) { _, existing ->
            val count = (existing?.count ?: 0) + 1
            FailureRecord(count, System.currentTimeMillis())
        }
        // Always evict stale entries (time-based, not just size-based) to prevent
        // distributed attacks from flushing active IP records via eviction
        val cutoff = System.currentTimeMillis() - EVICTION_MS
        failureCounts.entries.removeIf { it.value.lastFailure < cutoff }
        // Hard cap to prevent unbounded memory growth
        if (failureCounts.size > MAX_TRACKED_IPS) {
            val oldest = failureCounts.entries.minByOrNull { it.value.lastFailure }
            if (oldest != null) failureCounts.remove(oldest.key)
        }
    }

    private fun extractToken(request: Request): String? {
        // Prefer Authorization header over query parameter (query params risk leaking in logs/referer)
        val authHeader = request.headers["authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return authHeader.substring(7).trim()
        }
        return request.query["token"]
    }

    private fun resolveScope(inputToken: String): TokenScope? {
        tokenBytesMap[TokenScope.FULL]?.let {
            if (constantTimeEquals(inputToken, it)) return TokenScope.FULL
        }
        tokenBytesMap[TokenScope.READ]?.let {
            if (constantTimeEquals(inputToken, it)) return TokenScope.READ
        }
        return null
    }

    private fun requiresFullScope(request: Request): Boolean {
        return request.method != "GET" && request.method != "HEAD"
    }

    private fun constantTimeEquals(input: String, expected: ByteArray): Boolean {
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(inputBytes, expected)
    }

    companion object {
        private const val MAX_FAILURES = 5
        private const val BASE_BACKOFF_MS = 1_000L    // 1s initial backoff
        private const val MAX_BACKOFF_MS = 32_000L    // 32s max backoff
        private const val EVICTION_MS = 600_000L      // 10 min stale entry eviction
        private const val MAX_TRACKED_IPS = 1000      // Hard cap on tracked IPs
    }
}
