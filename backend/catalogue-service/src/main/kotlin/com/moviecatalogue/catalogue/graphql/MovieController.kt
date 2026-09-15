package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.MovieUseCases
import com.moviecatalogue.catalogue.application.MovieReadService
import com.moviecatalogue.catalogue.application.MovieFilter
import com.moviecatalogue.catalogue.domain.CreditCategory
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.UUID
import java.util.concurrent.CompletableFuture
import com.moviecatalogue.catalogue.application.PersonRef

/**
 * Movie query + nested field resolvers. Nested `cast`/`creators` return credits
 * whose `person` field is resolved via the request-scoped DataLoader, so a
 * `movies` page with many credits triggers a single batched gRPC call.
 */
@Controller
class MovieController(
    private val movieUseCases: MovieUseCases,
    private val movieReads: MovieReadService,
) {

    @QueryMapping
    fun movie(@Argument id: String): MovieGql? =
        runCatching { movieUseCases.getMovie(UUID.fromString(id)).toGql() }
            .getOrElse { if (it is com.moviecatalogue.catalogue.domain.NotFoundException) null else throw it }

    @QueryMapping
    fun movies(@Argument page: PageInput?, @Argument filter: MovieFilterInput?): MoviePageGql {
        val p = page ?: PageInput()
        val f = filter?.let { MovieFilter(it.genreCode, it.releaseYear) }
        val view = movieUseCases.listMovies(p.limit, p.offset, f)
        return MoviePageGql(view.items.map { it.toGql() }, view.total, view.limit, view.offset)
    }

    // --- Movie nested fields ---------------------------------------------

    @SchemaMapping(typeName = "Movie", field = "genres")
    fun genres(movie: MovieGql): List<GenreCodeGql> =
        movieReads.genres(UUID.fromString(movie.id)).map { it.toGql() }

    @SchemaMapping(typeName = "Movie", field = "cast")
    fun cast(movie: MovieGql): List<MovieCreditGql> =
        movieReads.credits(UUID.fromString(movie.id), CreditCategory.CAST)
            .map { it.toGql() }

    @SchemaMapping(typeName = "Movie", field = "creators")
    fun creators(movie: MovieGql): List<MovieCreditGql> =
        movieReads.credits(UUID.fromString(movie.id), CreditCategory.CREW)
            .map { it.toGql() }

    @SchemaMapping(typeName = "Movie", field = "artwork")
    fun artwork(movie: MovieGql): ArtworkGql? {
        val asset = movieReads.artwork(UUID.fromString(movie.id)) ?: return null
        // phase 4 serves bytes at /api/artwork/{id}; expose the stable URL now.
        return asset.toGql(url = "/api/artwork/${asset.id}")
    }

    // --- MovieCredit nested fields ---------------------------------------

    @SchemaMapping(typeName = "MovieCredit", field = "role")
    fun role(credit: MovieCreditGql): CreditRoleCodeGql =
        movieReads.role(credit.roleCode)?.toGql()
            ?: error("role ${credit.roleCode} missing")

    /**
     * Resolves the credit's person via the DataLoader so all credits on the page
     * batch into ONE gRPC call. Returns available:false for missing/unavailable.
     */
    @SchemaMapping(typeName = "MovieCredit", field = "person")
    fun person(
        credit: MovieCreditGql,
        env: graphql.schema.DataFetchingEnvironment,
    ): CompletableFuture<PersonReferenceGql> {
        val loader: DataLoader<UUID, PersonRef> = env.getDataLoader(PersonReferenceDataLoader.NAME)!!
        return loader.load(UUID.fromString(credit.personId)).thenApply { ref ->
            PersonReferenceGql(ref.id.toString(), ref.name, ref.available)
        }
    }
}
