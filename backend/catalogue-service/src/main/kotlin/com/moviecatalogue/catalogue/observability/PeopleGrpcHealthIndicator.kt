package com.moviecatalogue.catalogue.observability

import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Readiness contributor for People gRPC connectivity (§15). A lightweight probe
 * (a getPerson for a random id) confirms the channel is reachable: NOT_FOUND means
 * the service is up and answering, which is a healthy signal; only a transport/
 * dependency failure marks the service not-ready.
 */
@Component("peopleGrpc")
class PeopleGrpcHealthIndicator(
    private val peopleClient: PeopleClient,
) : HealthIndicator {

    override fun health(): Health {
        return try {
            peopleClient.getPerson(PROBE_ID)
            Health.up().withDetail("people", "reachable").build()
        } catch (e: DependencyUnavailableException) {
            Health.down().withDetail("people", "unavailable").build()
        } catch (e: Exception) {
            // NOT_FOUND / validation etc. mean People answered -> reachable/up.
            Health.up().withDetail("people", "reachable").build()
        }
    }

    private companion object {
        // A fixed random-but-nonexistent id; the point is round-trip reachability.
        val PROBE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }
}
