package com.moviecatalogue.catalogue.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Standard browser security headers (§14). The public entry point is the SvelteKit
 * BFF; these apply defense-in-depth to the Catalogue's own HTTP surface (GraphQL +
 * artwork media). No CORS is enabled: the browser never calls these endpoints
 * cross-origin (only the BFF does, server-to-server).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SecurityHeadersFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("Cross-Origin-Resource-Policy", "same-site")
        // No wildcard CORS: absence of Access-Control-Allow-Origin means browsers
        // block cross-origin reads. The BFF calls server-to-server, unaffected.
        filterChain.doFilter(request, response)
    }
}
