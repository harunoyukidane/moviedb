package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.LocalArtworkStore
import com.moviecatalogue.media.MinioArtworkStore
import com.moviecatalogue.people.observability.ObjectStorageHealthIndicator
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path

/**
 * Wires the shared media components for person-photo upload (ADR-12 hybrid).
 * Reuses the exact same validated ArtworkStore path as movie artwork. Storage
 * backend is config-driven (artwork.storage.type=local|minio, V2-02).
 */
@Configuration
class PhotoMediaConfig {

    @Bean
    @ConditionalOnProperty(prefix = "artwork.storage", name = ["type"], havingValue = "local", matchIfMissing = true)
    fun localArtworkStore(@Value("\${artwork.storage-path:./data/person-artwork}") path: String): ArtworkStore =
        LocalArtworkStore(Path.of(path))

    @Bean
    @ConditionalOnProperty(prefix = "artwork.storage", name = ["type"], havingValue = "minio")
    fun minioClient(
        @Value("\${artwork.storage.minio.endpoint}") endpoint: String,
        @Value("\${artwork.storage.minio.access-key}") accessKey: String,
        @Value("\${artwork.storage.minio.secret-key}") secretKey: String,
        @Value("\${artwork.storage.minio.region}") region: String,
    ): MinioClient =
        MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .region(region)
            .build()

    @Bean
    @ConditionalOnProperty(prefix = "artwork.storage", name = ["type"], havingValue = "minio")
    fun minioArtworkStore(
        client: MinioClient,
        @Value("\${artwork.storage.minio.bucket}") bucket: String,
    ): ArtworkStore {
        val reachable = try {
            client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        } catch (e: Exception) {
            throw IllegalStateException("cannot reach MinIO bucket '$bucket' at startup", e)
        }
        check(reachable) { "MinIO bucket '$bucket' does not exist or is not accessible to this service's credentials" }
        return MinioArtworkStore(client, bucket)
    }

    @Bean("objectStorage")
    @ConditionalOnProperty(prefix = "artwork.storage", name = ["type"], havingValue = "minio")
    fun objectStorageHealthIndicator(
        client: MinioClient,
        @Value("\${artwork.storage.minio.bucket}") bucket: String,
    ): HealthIndicator = ObjectStorageHealthIndicator(client, bucket)

    // The readiness group unconditionally lists "objectStorage" (application.yml), and Spring
    // Boot validates group membership eagerly by default - the bean must exist even when local
    // storage has no real backend to probe.
    @Bean("objectStorage")
    @ConditionalOnProperty(prefix = "artwork.storage", name = ["type"], havingValue = "local", matchIfMissing = true)
    fun localObjectStorageHealthIndicator(): HealthIndicator = HealthIndicator { Health.up().build() }

    @Bean
    fun imageContentValidator(): ImageContentValidator = ImageContentValidator()

    @Bean
    fun personPhotoTransactionTemplate(txManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(txManager)
}
