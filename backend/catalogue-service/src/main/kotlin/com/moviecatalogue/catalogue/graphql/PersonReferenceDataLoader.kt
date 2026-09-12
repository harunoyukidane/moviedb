package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.PersonHydrator
import com.moviecatalogue.catalogue.application.PersonRef
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import java.util.UUID
import jakarta.annotation.PostConstruct

/**
 * Registers a DataLoader that batches all person-reference lookups within a
 * single GraphQL request into ONE gRPC `GetPeople` call (§6.5, no N+1). The
 * MovieCredit.person field resolver loads via this loader; GraphQL coalesces the
 * keys across every credit on the page before the batch function runs.
 */
@Component
class PersonReferenceDataLoader(
    private val registry: BatchLoaderRegistry,
    private val hydrator: PersonHydrator,
) {
    companion object {
        const val NAME = "personReferenceLoader"
    }

    @PostConstruct
    fun register() {
        registry.forName<UUID, PersonRef>(NAME).registerMappedBatchLoader { keys, _ ->
            // hydrator.resolve already de-duplicates and tolerates dependency outage
            reactor.core.publisher.Mono.fromCallable { hydrator.resolve(keys) }
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        }
    }
}
