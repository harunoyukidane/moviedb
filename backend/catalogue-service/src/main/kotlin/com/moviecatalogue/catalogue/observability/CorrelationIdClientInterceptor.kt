package com.moviecatalogue.catalogue.observability

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor

/**
 * Copies the current correlation id (from MDC) into outgoing gRPC metadata so it
 * propagates from a GraphQL request into the People Service (§15). The key name
 * matches the People-side server interceptor.
 */
@GrpcGlobalClientInterceptor
class CorrelationIdClientInterceptor : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions),
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                CorrelationId.current()?.let { headers.put(CORRELATION_METADATA_KEY, it) }
                super.start(responseListener, headers)
            }
        }
    }

    companion object {
        val CORRELATION_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)
    }
}
