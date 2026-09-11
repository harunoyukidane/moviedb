package com.moviecatalogue.people.common

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Application-side UUIDv7 (time-ordered) primary-key generation (ADR-10).
 * Keys are near-monotonic, improving insert locality and reducing B-tree
 * fragmentation versus random UUIDv4.
 */
object UuidV7 {
    fun generate(): UUID = UuidCreator.getTimeOrderedEpoch()
}
