package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.AddCreditCommand
import com.moviecatalogue.catalogue.application.CommentUseCases
import com.moviecatalogue.catalogue.application.CreateMovieCommand
import com.moviecatalogue.catalogue.application.CreditUseCases
import com.moviecatalogue.catalogue.application.MovieUseCases
import com.moviecatalogue.catalogue.application.PersonUseCases
import com.moviecatalogue.catalogue.application.UpdateCreditCommand
import com.moviecatalogue.catalogue.application.UpdateMovieCommand
import com.moviecatalogue.catalogue.people.UpdatePersonData
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import java.time.LocalDate
import java.util.UUID

/**
 * All GraphQL mutations. Update mutations use the raw argument map to decide
 * field-mask membership (present vs. absent), so "set to null" is distinguishable
 * from "omitted". createMovie/createPerson validate in the use cases; the error
 * boundary maps failures to stable codes.
 */
@Controller
class MutationController(
    private val movieUseCases: MovieUseCases,
    private val creditUseCases: CreditUseCases,
    private val personUseCases: PersonUseCases,
    private val commentUseCases: CommentUseCases,
) {

    // --- Movie -----------------------------------------------------------

    @MutationMapping
    fun createMovie(@Argument input: CreateMovieInput): MovieGql =
        movieUseCases.createMovie(
            CreateMovieCommand(
                title = input.title,
                originalTitle = input.originalTitle,
                synopsis = input.synopsis,
                releaseDate = input.releaseDate,
                runtimeMinutes = input.runtimeMinutes,
                originalLanguage = input.originalLanguage,
                genreCodes = input.genreCodes,
                tmdbId = input.tmdbId,
            ),
        ).toGql()

    @MutationMapping
    fun updateMovie(
        @Argument id: String,
        @Argument expectedVersion: Long,
        @Argument input: UpdateMovieInput,
        env: graphql.schema.DataFetchingEnvironment,
    ): MovieGql {
        @Suppress("UNCHECKED_CAST")
        val present = (env.getArgument<Map<String, Any?>>("input") ?: emptyMap()).keys
        return movieUseCases.updateMovie(
            UpdateMovieCommand(
                id = UUID.fromString(id),
                expectedVersion = expectedVersion,
                maskTitle = "title" in present, title = input.title,
                maskOriginalTitle = "originalTitle" in present, originalTitle = input.originalTitle,
                maskSynopsis = "synopsis" in present, synopsis = input.synopsis,
                maskReleaseDate = "releaseDate" in present, releaseDate = input.releaseDate,
                maskRuntime = "runtimeMinutes" in present, runtimeMinutes = input.runtimeMinutes,
                maskOriginalLanguage = "originalLanguage" in present, originalLanguage = input.originalLanguage,
                maskGenres = "genreCodes" in present, genreCodes = input.genreCodes,
            ),
        ).toGql()
    }

    @MutationMapping
    fun deleteMovie(@Argument id: String): DeleteResultGql =
        DeleteResultGql(movieUseCases.deleteMovie(UUID.fromString(id)).toString())

    // --- Person (orchestrated over gRPC) ---------------------------------

    @MutationMapping
    fun createPerson(@Argument input: CreatePersonInput): PersonGql =
        personUseCases.createPerson(
            name = input.name,
            biography = input.biography,
            birthDate = input.birthDate,
            deathDate = input.deathDate,
            placeOfBirth = input.placeOfBirth,
        ).toGql()

    @MutationMapping
    fun updatePerson(
        @Argument id: String,
        @Argument expectedVersion: Long,
        @Argument input: UpdatePersonInput,
        env: graphql.schema.DataFetchingEnvironment,
    ): PersonGql {
        @Suppress("UNCHECKED_CAST")
        val present = (env.getArgument<Map<String, Any?>>("input") ?: emptyMap()).keys
        val maskPaths = buildSet {
            if ("name" in present) add("name")
            if ("biography" in present) add("biography")
            if ("birthDate" in present) add("birth_date")
            if ("deathDate" in present) add("death_date")
            if ("placeOfBirth" in present) add("place_of_birth")
        }
        return personUseCases.updatePerson(
            UpdatePersonData(
                id = UUID.fromString(id),
                expectedVersion = expectedVersion,
                maskPaths = maskPaths,
                name = input.name,
                biography = input.biography,
                birthDate = input.birthDate,
                deathDate = input.deathDate,
                placeOfBirth = input.placeOfBirth,
            ),
        ).toGql()
    }

    @MutationMapping
    fun deletePerson(@Argument id: String): DeleteResultGql =
        DeleteResultGql(personUseCases.deletePerson(UUID.fromString(id)).toString())

    // --- Credits ---------------------------------------------------------

    @MutationMapping
    fun addMovieCredit(@Argument movieId: String, @Argument input: CreateCreditInput): MovieCreditGql =
        creditUseCases.addCredit(
            AddCreditCommand(
                movieId = UUID.fromString(movieId),
                personId = UUID.fromString(input.personId),
                roleCode = input.roleCode,
                characterName = input.characterName,
                billingOrder = input.billingOrder,
                tmdbCreditId = input.tmdbCreditId,
                sourceRoleName = input.sourceRoleName,
            ),
        ).toGql()

    @MutationMapping
    fun updateMovieCredit(
        @Argument id: String,
        @Argument input: UpdateCreditInput,
        env: graphql.schema.DataFetchingEnvironment,
    ): MovieCreditGql {
        @Suppress("UNCHECKED_CAST")
        val present = (env.getArgument<Map<String, Any?>>("input") ?: emptyMap()).keys
        return creditUseCases.updateCredit(
            UpdateCreditCommand(
                id = UUID.fromString(id),
                maskRole = "roleCode" in present, roleCode = input.roleCode,
                maskCharacter = "characterName" in present, characterName = input.characterName,
                maskBilling = "billingOrder" in present, billingOrder = input.billingOrder,
            ),
        ).toGql()
    }

    @MutationMapping
    fun removeMovieCredit(@Argument id: String): DeleteResultGql =
        DeleteResultGql(creditUseCases.removeCredit(UUID.fromString(id)).toString())

    // --- Comments ----------------------------------------------------------

    @MutationMapping
    fun addMovieComment(@Argument movieId: String, @Argument input: AddMovieCommentInput): MovieCommentGql =
        commentUseCases.addComment(UUID.fromString(movieId), input.authorDisplayName, input.text).toGql()
}
