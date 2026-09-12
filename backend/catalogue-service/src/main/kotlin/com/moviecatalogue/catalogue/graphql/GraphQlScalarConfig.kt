package com.moviecatalogue.catalogue.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Registers the `Date` (ISO yyyy-MM-dd) and `Long` (64-bit) custom scalars (§8.1). */
@Configuration
class GraphQlScalarConfig {

    @Bean
    fun scalarConfigurer(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { wiring ->
            wiring.scalar(dateScalar()).scalar(longScalar())
        }

    private fun dateScalar(): GraphQLScalarType {
        val iso = DateTimeFormatter.ISO_LOCAL_DATE
        val coercing = object : Coercing<LocalDate, String> {
            override fun serialize(dataFetcherResult: Any, ctx: GraphQLContext, locale: Locale): String =
                when (dataFetcherResult) {
                    is LocalDate -> dataFetcherResult.format(iso)
                    else -> throw CoercingSerializeException("expected LocalDate, got ${dataFetcherResult::class}")
                }

            override fun parseValue(input: Any, ctx: GraphQLContext, locale: Locale): LocalDate =
                try {
                    LocalDate.parse(input.toString(), iso)
                } catch (e: DateTimeParseException) {
                    throw CoercingParseValueException("invalid Date '$input'; expected ISO yyyy-MM-dd")
                }

            override fun parseLiteral(input: Value<*>, vars: CoercedVariables, ctx: GraphQLContext, locale: Locale): LocalDate {
                if (input !is StringValue) throw CoercingParseLiteralException("Date must be a string literal")
                return try {
                    LocalDate.parse(input.value, iso)
                } catch (e: DateTimeParseException) {
                    throw CoercingParseLiteralException("invalid Date '${input.value}'")
                }
            }
        }
        return GraphQLScalarType.newScalar().name("Date").description("ISO-8601 date (yyyy-MM-dd)").coercing(coercing).build()
    }

    private fun longScalar(): GraphQLScalarType {
        val coercing = object : Coercing<Long, Long> {
            override fun serialize(dataFetcherResult: Any, ctx: GraphQLContext, locale: Locale): Long =
                when (dataFetcherResult) {
                    is Long -> dataFetcherResult
                    is Int -> dataFetcherResult.toLong()
                    is Number -> dataFetcherResult.toLong()
                    else -> throw CoercingSerializeException("expected Long, got ${dataFetcherResult::class}")
                }

            override fun parseValue(input: Any, ctx: GraphQLContext, locale: Locale): Long =
                when (input) {
                    is Number -> input.toLong()
                    is String -> input.toLongOrNull() ?: throw CoercingParseValueException("invalid Long '$input'")
                    else -> throw CoercingParseValueException("invalid Long '$input'")
                }

            override fun parseLiteral(input: Value<*>, vars: CoercedVariables, ctx: GraphQLContext, locale: Locale): Long =
                when (input) {
                    is IntValue -> input.value.toLong()
                    is StringValue -> input.value.toLongOrNull() ?: throw CoercingParseLiteralException("invalid Long '${input.value}'")
                    else -> throw CoercingParseLiteralException("Long must be an integer literal")
                }
        }
        return GraphQLScalarType.newScalar().name("Long").description("64-bit signed integer").coercing(coercing).build()
    }
}
