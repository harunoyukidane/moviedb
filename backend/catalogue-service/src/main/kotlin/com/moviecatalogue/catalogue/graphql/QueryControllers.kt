package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.CommentUseCases
import com.moviecatalogue.catalogue.application.PersonUseCases
import com.moviecatalogue.catalogue.application.ReferenceUseCases
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.domain.NotFoundException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PersonQueryController(
    private val personUseCases: PersonUseCases,
) {

    @QueryMapping
    fun person(@Argument id: String): PersonGql? =
        runCatching { personUseCases.getPerson(parseId(id, "person")).toGql() }
            .getOrElse { if (it is NotFoundException) null else throw it }

    @QueryMapping
    fun people(@Argument query: String?, @Argument page: PageInput?): PersonPageGql {
        val p = page ?: PageInput()
        val result = personUseCases.listPeople(query, p.limit, p.offset)
        return PersonPageGql(
            items = result.items.map { it.toGql() },
            total = result.total,
            limit = result.limit,
            offset = result.offset,
        )
    }

    @SchemaMapping(typeName = "Person", field = "credits")
    fun credits(person: PersonGql): List<PersonCreditGql> =
        personUseCases.creditsForPerson(parseId(person.id, "person")).map { it.toGql() }

    @QueryMapping
    fun countryCodes(@Argument activeOnly: Boolean?): List<CountryCodeGql> =
        personUseCases.listCountries(activeOnly ?: true).map { it.toGql() }

    /**
     * Resolved alongside the raw `birthCountryCode`, matching what `Movie.language`
     * does for `originalLanguage` - the frontend gets the display name without a
     * second round trip. Unlike Movie.language (a local Catalogue DB lookup), this
     * one call cost is a People gRPC round trip; acceptable because the frontend
     * only ever requests it on the single-person detail/edit view, never in a list.
     */
    @SchemaMapping(typeName = "Person", field = "birthCountry")
    fun birthCountry(person: PersonGql): CountryCodeGql? {
        val code = person.birthCountryCode ?: return null
        return personUseCases.listCountries(activeOnly = false).find { it.code == code }?.toGql()
    }
}

@Controller
class ReferenceQueryController(
    private val referenceUseCases: ReferenceUseCases,
) {

    @QueryMapping
    fun genres(@Argument activeOnly: Boolean?): List<GenreCodeGql> =
        referenceUseCases.listGenres(activeOnly ?: true).map { it.toGql() }

    @QueryMapping
    fun creditRoles(
        @Argument category: CreditCategory?,
        @Argument activeOnly: Boolean?,
    ): List<CreditRoleCodeGql> =
        referenceUseCases.listCreditRoles(category, activeOnly ?: true).map { it.toGql() }

    @QueryMapping
    fun languageCodes(@Argument activeOnly: Boolean?): List<LanguageCodeGql> =
        referenceUseCases.listLanguages(activeOnly ?: true).map { it.toGql() }

    /** PersonCredit.role resolver (shared shape). */
    @SchemaMapping(typeName = "PersonCredit", field = "role")
    fun role(credit: PersonCreditGql): CreditRoleCodeGql =
        referenceUseCases.roleByCode(credit.roleCode)?.toGql()
            ?: error("role ${credit.roleCode} missing")
}

@Controller
class CommentQueryController(
    private val commentUseCases: CommentUseCases,
) {

    @QueryMapping
    fun comments(@Argument movieId: String, @Argument page: PageInput?): MovieCommentPageGql {
        val p = page ?: PageInput()
        val result = commentUseCases.listComments(parseId(movieId, "movie"), p.limit, p.offset)
        return MovieCommentPageGql(
            items = result.items.map { it.toGql() },
            total = result.total,
            limit = result.limit,
            offset = result.offset,
        )
    }
}

@Controller
class SearchController(
    private val searchUseCases: com.moviecatalogue.catalogue.application.SearchUseCases,
) {
    /**
     * Unified search (§9). Returns matching movies (with matched person names) and
     * people. Blank queries are rejected via the error boundary (BAD_USER_INPUT).
     */
    @QueryMapping
    fun search(@Argument query: String, @Argument page: PageInput?): SearchResultGql {
        val p = page ?: PageInput()
        val result = searchUseCases.search(query, p.limit, p.offset)
        return SearchResultGql(
            movies = result.movies.map {
                MovieSearchHitGql(it.id.toString(), it.title, it.releaseDate, it.matchedPersonNames)
            },
            people = result.people.map { PersonSearchHitGql(it.id.toString(), it.name) },
        )
    }
}
