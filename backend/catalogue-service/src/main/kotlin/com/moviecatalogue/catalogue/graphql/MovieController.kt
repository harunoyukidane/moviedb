package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.MovieUseCases
import com.moviecatalogue.catalogue.application.ReferenceUseCases
import com.moviecatalogue.catalogue.artwork.ArtworkRepository
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.movie.MovieGenreRepository
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
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
    private val referenceUseCases: ReferenceUseCases,
    private val credits: CreditRepository,
    private val movieGenres: MovieGenreRepository,
    private val genreCodes: GenreCodeRepository,
    private val roleCodes: CreditRoleCodeRepository,
    private val artwork: ArtworkRepository,
) {

    @QueryMapping
    fun movie(@Argument id: String): MovieGql? =
        runCatching { movieUseCases.getMovie(UUID.fromString(id)).toGql() }
            .getOrElse { if (it is com.moviecatalogue.catalogue.domain.NotFoundException) null else throw it }

    @QueryMapping
    fun movies(@Argument page: PageInput?): MoviePageGql {
        val p = page ?: PageInput()
        val view = movieUseCases.listMovies(p.limit, p.offset)
        return MoviePageGql(view.items.map { it.toGql() }, view.total, view.limit, view.offset)
    }

    // --- Movie nested fields ---------------------------------------------

    @SchemaMapping(typeName = "Movie", field = "genres")
    fun genres(movie: MovieGql): List<GenreCodeGql> {
        val codes = movieGenres.findAllByIdMovieId(UUID.fromString(movie.id)).map { it.id.genreCode }
        if (codes.isEmpty()) return emptyList()
        return genreCodes.findAllById(codes).sortedBy { it.displayOrder }.map { it.toGql() }
    }

    @SchemaMapping(typeName = "Movie", field = "cast")
    fun cast(movie: MovieGql): List<MovieCreditGql> =
        credits.findAllByMovieId(UUID.fromString(movie.id))
            .filter { it.category == CreditCategory.CAST }
            .sortedWith(compareBy({ it.billingOrder ?: Int.MAX_VALUE }, { it.id }))
            .map { it.toGql() }

    @SchemaMapping(typeName = "Movie", field = "creators")
    fun creators(movie: MovieGql): List<MovieCreditGql> =
        credits.findAllByMovieId(UUID.fromString(movie.id))
            .filter { it.category == CreditCategory.CREW }
            .sortedWith(compareBy({ it.roleCode }, { it.billingOrder ?: Int.MAX_VALUE }, { it.id }))
            .map { it.toGql() }

    @SchemaMapping(typeName = "Movie", field = "artwork")
    fun artwork(movie: MovieGql): ArtworkGql? {
        val asset = artwork.findByMovieId(UUID.fromString(movie.id)) ?: return null
        // phase 4 serves bytes at /api/artwork/{id}; expose the stable URL now.
        return asset.toGql(url = "/api/artwork/${asset.id}")
    }

    // --- MovieCredit nested fields ---------------------------------------

    @SchemaMapping(typeName = "MovieCredit", field = "role")
    fun role(credit: MovieCreditGql): CreditRoleCodeGql =
        roleCodes.findById(credit.roleCode).map { it.toGql() }
            .orElseThrow { IllegalStateException("role ${credit.roleCode} missing") }

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
