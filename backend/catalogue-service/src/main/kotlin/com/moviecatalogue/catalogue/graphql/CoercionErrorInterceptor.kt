package com.moviecatalogue.catalogue.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Variable coercion and syntax failures (e.g. a malformed `Date` scalar like
 * "3/3/52452242") happen during coercion, before execution starts, so
 * GraphQlExceptionResolver — a DataFetcherExceptionResolver — never sees them
 * (V2.2-04, F5). Left alone they carry no `extensions.code`, and `gql()`
 * defaults an uncoded error to INTERNAL_ERROR. This is the one interceptor hook
 * that runs after such an error already exists, so it stamps BAD_USER_INPUT on
 * any pre-execution error that isn't already coded.
 */
@Component
class CoercionErrorInterceptor : WebGraphQlInterceptor {

    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> =
        chain.next(request).map { response ->
            val errors = response.executionResult.errors
            if (errors.isEmpty() || errors.none(::isUncodedPreExecutionError)) {
                response
            } else {
                val remapped = errors.map { if (isUncodedPreExecutionError(it)) stampBadUserInput(it) else it }
                response.transform { it.errors(remapped) }
            }
        }

    private fun isUncodedPreExecutionError(error: GraphQLError): Boolean {
        if (error.extensions?.containsKey("code") == true) return false
        return error.errorType == graphql.ErrorType.ValidationError || error.errorType == graphql.ErrorType.InvalidSyntax
    }

    private fun stampBadUserInput(error: GraphQLError): GraphQLError =
        GraphqlErrorBuilder.newError()
            .message(error.message)
            .locations(error.locations)
            .errorType(ErrorType.BAD_REQUEST)
            .extensions(mapOf("code" to "BAD_USER_INPUT"))
            .build()
}
