package com.moviecatalogue.catalogue.artwork

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Metadata row for a movie's artwork (§7.2). Byte storage and the media
 * endpoints arrive in phase 4; here the row backs the GraphQL `Artwork` field
 * (metadata + URL only, may be null until phase 4).
 */
@Entity
@Table(name = "artwork_asset")
class ArtworkAsset(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "movie_id", nullable = false)
    var movieId: UUID,

    @Column(name = "storage_key", nullable = false)
    var storageKey: String,

    @Column(name = "original_filename", nullable = false)
    var originalFilename: String,

    @Column(name = "media_type", nullable = false)
    var mediaType: String,

    @Column(name = "byte_size", nullable = false)
    var byteSize: Long,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, length = 64)
    var sha256: String,

    @Column(name = "width")
    var width: Int? = null,

    @Column(name = "height")
    var height: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: OffsetDateTime? = null,
)
