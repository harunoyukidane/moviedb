package com.moviecatalogue.catalogue.artwork

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ArtworkRepository : JpaRepository<ArtworkAsset, UUID> {
    /** One primary artwork per movie (uq_movie_primary_artwork, §7.2). */
    fun findByMovieId(movieId: UUID): ArtworkAsset?
    fun findAllByMovieIdIn(movieIds: Collection<UUID>): List<ArtworkAsset>

    /** All storage keys currently referenced by metadata (orphan sweeper). */
    @Query("SELECT a.storageKey FROM ArtworkAsset a")
    fun findAllStorageKeys(): List<String>
}
