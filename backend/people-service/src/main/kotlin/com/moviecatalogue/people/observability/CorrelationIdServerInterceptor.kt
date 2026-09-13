package com.moviecatalogue.people.observability

import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.slf4j.MDC
import java.util.UUID

/**
 * Reads the incoming `x-correlation-id` gRPC metadata (set by the Catalogue
 * client) into the SLF4J MDC so People log lines carry the same correlation id
 * end-to-end (§15). Generates one if absent. The MDC is cleared when the call's
 * listener is closed.
 */
@GrpcGlobalServerInterceptor
class CorrelationIdServerInterceptor : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val incoming = headers.get(CORRELATION_METADATA_KEY)
        val correlationId = if (incoming.isNullOrBlank()) UUID.randomUUID().toString() else incoming
        MDC.put(MDC_KEY, correlationId)
        val listener = Contexts.interceptCall(io.grpc.Context.current(), call, headers, next)
        return object : io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
            override fun onComplete() {
                try { super.onComplete() } finally { MDC.remove(MDC_KEY) }
            }
            override fun onCancel() {
                try { super.onCancel() } finally { MDC.remove(MDC_KEY) }
            }
        }
    }

    companion object {
        const val MDC_KEY = "correlationId"
        val CORRELATION_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)
    }
}
