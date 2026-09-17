package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.PersonInUseException
import com.moviecatalogue.catalogue.domain.ValidationException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.slf4j.LoggerFactory
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Single error-mapping boundary (§8.2). Translates domain exceptions to stable
 * `extensions.code` values and safe messages. Unexpected exceptions become
 * INTERNAL_ERROR with no stack trace or PII leaked.
 */
@Component
class GraphQlExceptionResolver : DataFetcherExceptionResolverAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError {
        val mapped = classify(ex)
        if (mapped.code == "INTERNAL_ERROR") {
            // Log the real cause server-side (with correlation via MDC in later phases),
            // but never expose it to the client.
            log.error("Unhandled GraphQL error at {}", env.field?.name, ex)
        }
        val extensions = mutableMapOf<String, Any>("code" to mapped.code)
        mapped.field?.let { extensions["field"] = it }
        return GraphqlErrorBuilder.newError(env)
            .errorType(mapped.type)
            .message(mapped.message)
            .extensions(extensions)
            .build()
    }

    private data class Mapped(val code: String, val type: ErrorType, val message: String, val field: String? = null)

    private fun classify(ex: Throwable): Mapped = when (ex) {
        is ValidationException -> Mapped("BAD_USER_INPUT", ErrorType.BAD_REQUEST, ex.message ?: "invalid input", ex.field)
        is NotFoundException -> Mapped("NOT_FOUND", ErrorType.NOT_FOUND, ex.message ?: "not found")
        is ConflictException -> Mapped("CONFLICT", ErrorType.BAD_REQUEST, ex.message ?: "conflict")
        is PersonInUseException -> Mapped("PERSON_IN_USE", ErrorType.BAD_REQUEST, ex.message ?: "person in use")
        is DependencyUnavailableException -> Mapped("DEPENDENCY_UNAVAILABLE", ErrorType.INTERNAL_ERROR, "a dependency is temporarily unavailable")
        else -> Mapped("INTERNAL_ERROR", ErrorType.INTERNAL_ERROR, "internal error")
    }
}
