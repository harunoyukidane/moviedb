package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.PersonUseCases
import com.moviecatalogue.catalogue.application.ReferenceUseCases
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.domain.NotFoundException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class PersonQueryController(
    private val personUseCases: PersonUseCases,
) {

    @QueryMapping
    fun person(@Argument id: String): PersonGql? =
        runCatching { personUseCases.getPerson(UUID.fromString(id)).toGql() }
            .getOrElse { if (it is NotFoundException) null else throw it }

    @SchemaMapping(typeName = "Person", field = "credits")
    fun credits(person: PersonGql): List<PersonCreditGql> =
        personUseCases.creditsForPerson(UUID.fromString(person.id)).map { it.toGql() }
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

    /** PersonCredit.role resolver (shared shape). */
    @SchemaMapping(typeName = "PersonCredit", field = "role")
    fun role(credit: PersonCreditGql): CreditRoleCodeGql =
        referenceUseCases.roleByCode(credit.roleCode)?.toGql()
            ?: error("role ${credit.roleCode} missing")
}

@Controller
class SearchController {
    /**
     * Search is implemented in phase 6. The field is wired so the schema is
     * complete; it returns empty results for now (blank/normal handling lands
     * with the real implementation).
     */
    @QueryMapping
    fun search(@Argument query: String, @Argument page: PageInput?): SearchResultGql =
        SearchResultGql(movies = emptyList(), people = emptyList())
}
