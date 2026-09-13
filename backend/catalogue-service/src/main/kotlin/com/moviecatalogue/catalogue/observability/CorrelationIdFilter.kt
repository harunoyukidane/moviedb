package com.moviecatalogue.catalogue.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Correlation-ID filter (§15). Reads the inbound `X-Correlation-ID` (set by the
 * BFF) or generates one, places it in the SLF4J MDC so every log line carries it,
 * and echoes it back on the response. A downstream gRPC interceptor copies it into
 * gRPC metadata so the id spans GraphQL -> gRPC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader(CorrelationId.HEADER)
        val correlationId = if (incoming.isNullOrBlank()) UUID.randomUUID().toString() else incoming
        MDC.put(CorrelationId.MDC_KEY, correlationId)
        response.setHeader(CorrelationId.HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CorrelationId.MDC_KEY)
        }
    }
}

object CorrelationId {
    const val HEADER = "X-Correlation-ID"
    const val MDC_KEY = "correlationId"

    fun current(): String? = MDC.get(MDC_KEY)
}
