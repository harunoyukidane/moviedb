package com.moviecatalogue.catalogue.graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Guards the public GraphQL API against abusive queries (§14): a hard maximum
 * query depth and complexity. Combined with the 1–100 pagination clamp, this
 * bounds the work any single request can request.
 */
@Configuration
class GraphQlSecurityConfig {

    @Bean
    fun maxDepthInstrumentation(): Instrumentation = MaxQueryDepthInstrumentation(MAX_DEPTH)

    @Bean
    fun maxComplexityInstrumentation(): Instrumentation = MaxQueryComplexityInstrumentation(MAX_COMPLEXITY)

    private companion object {
        const val MAX_DEPTH = 12
        const val MAX_COMPLEXITY = 300
    }
}
