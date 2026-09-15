package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.CatalogueServiceApplication
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.LocalArtworkStore
import com.moviecatalogue.media.MinioArtworkStore
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.WebApplicationType
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Proves storage backend selection (V2-02) is genuinely config-driven: [ArtworkStoreLocalSelectionTest]
 * and [ArtworkStoreMinioSelectionTest] assert the two bean-selection outcomes; [ArtworkStoreFailFastTest]
 * asserts startup fails clearly when MinIO config is incomplete or the bucket is unreachable.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtworkStoreLocalSelectionTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("grpc.client.people-service.address") { "in-process:artwork-config-local-it" }
            // artwork.storage.type left unset -> defaults to "local"
        }
    }

    @Autowired lateinit var store: ArtworkStore

    @Test
    fun `defaults to the local filesystem store`() {
        assertThat(store).isInstanceOf(LocalArtworkStore::class.java)
    }
}

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtworkStoreMinioSelectionTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val minio = MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z")
                .asCompatibleSubstituteFor("minio/minio"),
        )

        private const val BUCKET = "catalogue-config-test-bucket"

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("grpc.client.people-service.address") { "in-process:artwork-config-minio-it" }

            val client = MinioClient.builder()
                .endpoint(minio.s3URL)
                .credentials(minio.userName, minio.password)
                .build()
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build())
            }

            registry.add("artwork.storage.type") { "minio" }
            registry.add("artwork.storage.minio.endpoint") { minio.s3URL }
            registry.add("artwork.storage.minio.access-key") { minio.userName }
            registry.add("artwork.storage.minio.secret-key") { minio.password }
            registry.add("artwork.storage.minio.bucket") { BUCKET }
            registry.add("artwork.storage.minio.region") { "us-east-1" }
        }
    }

    @Autowired lateinit var store: ArtworkStore

    @Autowired
    @Qualifier("objectStorage")
    lateinit var objectStorageHealth: HealthIndicator

    @Test
    fun `selects the MinIO-backed store when type is minio`() {
        assertThat(store).isInstanceOf(MinioArtworkStore::class.java)
    }

    @Test
    fun `registers an UP readiness indicator for object storage`() {
        assertThat(objectStorageHealth.health().status).isEqualTo(Status.UP)
    }
}

@Testcontainers
class ArtworkStoreFailFastTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val minio = MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z")
                .asCompatibleSubstituteFor("minio/minio"),
        )
    }

    private fun baseProperties(grpcAddress: String) = arrayOf(
        "spring.datasource.url=${postgres.jdbcUrl}",
        "spring.datasource.username=${postgres.username}",
        "spring.datasource.password=${postgres.password}",
        "grpc.client.people-service.address=$grpcAddress",
        "artwork.storage.type=minio",
        "artwork.storage.minio.endpoint=${minio.s3URL}",
        "artwork.storage.minio.region=us-east-1",
    )

    // Passed as command-line-style "--key=value" args to SpringApplicationBuilder.run(), not
    // .properties() - the latter registers Spring Boot's lowest-precedence "default properties"
    // source, which application.yml's own spring.datasource.url placeholder-with-fallback
    // (${CATALOGUE_DB_URL:jdbc:postgresql://localhost:5432/catalogue}) would otherwise win over,
    // silently pointing this test at the wrong Postgres instance.
    // Which exception surfaces first (Spring's own "could not resolve placeholder" vs. the
    // MinIO SDK's "AccessKey and SecretKey must not be empty") isn't a stable contract - both
    // mean the same thing: startup failed clearly because the access key is missing.
    @Test
    fun `startup fails clearly when a required MinIO property is missing`() {
        val thrown = catchThrowable {
            SpringApplicationBuilder(CatalogueServiceApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    *baseProperties("in-process:artwork-config-missing-prop-it").map { "--$it" }.toTypedArray(),
                    // access-key and secret-key intentionally omitted
                )
        }
        var rootCause = thrown
        while (rootCause.cause != null) rootCause = rootCause.cause!!
        assertThat(rootCause.message).containsAnyOf("access-key", "AccessKey")
    }

    @Test
    fun `startup fails clearly when the configured bucket does not exist`() {
        assertThatThrownBy {
            SpringApplicationBuilder(CatalogueServiceApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    *baseProperties("in-process:artwork-config-missing-bucket-it").map { "--$it" }.toTypedArray(),
                    "--artwork.storage.minio.access-key=${minio.userName}",
                    "--artwork.storage.minio.secret-key=${minio.password}",
                    "--artwork.storage.minio.bucket=bucket-that-does-not-exist",
                )
        }.rootCause()
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("bucket-that-does-not-exist")
    }
}
