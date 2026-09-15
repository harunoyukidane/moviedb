package com.moviecatalogue.catalogue.observability

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

/**
 * Readiness contributor for object storage (V2-02). Only registered when
 * artwork.storage.type=minio (see MediaConfig); a bucketExists probe confirms
 * connectivity and that the configured bucket is still reachable.
 */
class ObjectStorageHealthIndicator(
    private val client: MinioClient,
    private val bucket: String,
) : HealthIndicator {

    override fun health(): Health {
        return try {
            if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                Health.up().withDetail("objectStorage", "reachable").build()
            } else {
                Health.down().withDetail("objectStorage", "bucket unavailable").build()
            }
        } catch (e: Exception) {
            Health.down().withDetail("objectStorage", "unavailable").build()
        }
    }
}
