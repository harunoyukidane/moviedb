package com.moviecatalogue.media

import io.minio.MinioClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayInputStream

/**
 * Proves the "separate least-privilege identity per bucket" design decision (tasks.md §8):
 * a service's credentials can fully operate on its own bucket but get AccessDenied against
 * the other service's bucket, for every ArtworkStore operation including listKeys(). Since
 * the core io.minio SDK cannot create users/policies (that's the MinIO admin API), this test
 * drives a minio/mc sidecar container via execInContainer instead of pulling in the
 * separately-versioned io.minio:minio-admin artifact - the same mechanism V2-03's real
 * bucket/identity/policy bootstrap will need in Compose.
 */
@Testcontainers
class CrossBucketIsolationTest {

    companion object {
        private val network: Network = Network.newNetwork()

        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z")
                .asCompatibleSubstituteFor("minio/minio"),
        ).withNetwork(network).withNetworkAliases("minio")

        @Container
        @JvmStatic
        val mc: GenericContainer<*> = GenericContainer(DockerImageName.parse("quay.io/minio/mc:latest"))
            .withNetwork(network)
            // override the image's fixed "mc" entrypoint with a long-running idle process so
            // the container stays up for execInContainer to drive; the whole argv must be set
            // here (not split into a separate withCommand call, which tokenizes its String
            // argument and would turn "sleep infinity" into two args passed to `sh -c`).
            .withCreateContainerCmdModifier { it.withEntrypoint("sh", "-c", "sleep infinity") }

        private const val CATALOGUE_BUCKET = "catalogue-artwork"
        private const val PEOPLE_BUCKET = "people-photos"
        private const val CATALOGUE_USER = "catalogue-app"
        private const val CATALOGUE_PASS = "catalogue-app-secret"
        private const val PEOPLE_USER = "people-app"
        private const val PEOPLE_PASS = "people-app-secret"

        private lateinit var catalogueClient: MinioClient
        private lateinit var peopleClient: MinioClient

        private fun runMc(vararg args: String) {
            val result = mc.execInContainer(*args)
            check(result.exitCode == 0) {
                "mc command failed (${args.joinToString(" ")}): stdout=${result.stdout} stderr=${result.stderr}"
            }
        }

        private fun scopedPolicyJson(bucket: String) = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"],
                  "Resource": ["arn:aws:s3:::$bucket", "arn:aws:s3:::$bucket/*"]
                }
              ]
            }
        """.trimIndent()

        @BeforeAll
        @JvmStatic
        fun provisionBucketsUsersAndPolicies() {
            runMc("mc", "alias", "set", "local", "http://minio:9000", minio.userName, minio.password)
            runMc("mc", "mb", "local/$CATALOGUE_BUCKET")
            runMc("mc", "mb", "local/$PEOPLE_BUCKET")

            runMc("mc", "admin", "user", "add", "local", CATALOGUE_USER, CATALOGUE_PASS)
            runMc("mc", "admin", "user", "add", "local", PEOPLE_USER, PEOPLE_PASS)

            runMc("sh", "-c", "cat > /tmp/catalogue-policy.json << 'EOF'\n${scopedPolicyJson(CATALOGUE_BUCKET)}\nEOF")
            runMc("sh", "-c", "cat > /tmp/people-policy.json << 'EOF'\n${scopedPolicyJson(PEOPLE_BUCKET)}\nEOF")

            runMc("mc", "admin", "policy", "create", "local", "catalogue-policy", "/tmp/catalogue-policy.json")
            runMc("mc", "admin", "policy", "create", "local", "people-policy", "/tmp/people-policy.json")
            runMc("mc", "admin", "policy", "attach", "local", "catalogue-policy", "--user", CATALOGUE_USER)
            runMc("mc", "admin", "policy", "attach", "local", "people-policy", "--user", PEOPLE_USER)

            // region must be set explicitly - without it, the SDK issues an automatic
            // GetBucketLocation call to auto-detect it, which needs s3:GetBucketLocation
            // that these least-privilege policies deliberately don't grant (matches how
            // MediaConfig always supplies a region for the production MinioClient beans).
            catalogueClient = MinioClient.builder()
                .endpoint(minio.s3URL)
                .credentials(CATALOGUE_USER, CATALOGUE_PASS)
                .region("us-east-1")
                .build()
            peopleClient = MinioClient.builder()
                .endpoint(minio.s3URL)
                .credentials(PEOPLE_USER, PEOPLE_PASS)
                .region("us-east-1")
                .build()
        }
    }

    @Test
    fun `each service can fully operate on its own bucket`() {
        val catalogueStore = MinioArtworkStore(catalogueClient, CATALOGUE_BUCKET)
        val peopleStore = MinioArtworkStore(peopleClient, PEOPLE_BUCKET)

        val a = catalogueStore.put(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "jpg")
        assertThat(catalogueStore.exists(a.storageKey)).isTrue()
        assertThat(catalogueStore.listKeys()).contains(a.storageKey)
        assertThat(catalogueStore.delete(a.storageKey)).isTrue()

        val b = peopleStore.put(ByteArrayInputStream(byteArrayOf(4, 5, 6)), "png")
        assertThat(peopleStore.exists(b.storageKey)).isTrue()
        assertThat(peopleStore.listKeys()).contains(b.storageKey)
        assertThat(peopleStore.delete(b.storageKey)).isTrue()
    }

    @Test
    fun `a service's credentials cannot read, write, delete, or list the other service's bucket`() {
        val seededInPeopleBucket = MinioArtworkStore(peopleClient, PEOPLE_BUCKET)
            .put(ByteArrayInputStream(byteArrayOf(9)), "jpg")
        val seededInCatalogueBucket = MinioArtworkStore(catalogueClient, CATALOGUE_BUCKET)
            .put(ByteArrayInputStream(byteArrayOf(8)), "jpg")

        val catalogueOnPeopleBucket = MinioArtworkStore(catalogueClient, PEOPLE_BUCKET)
        assertThatThrownBy { catalogueOnPeopleBucket.put(ByteArrayInputStream(byteArrayOf(7)), "png") }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { catalogueOnPeopleBucket.open(seededInPeopleBucket.storageKey) }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { catalogueOnPeopleBucket.delete(seededInPeopleBucket.storageKey) }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { catalogueOnPeopleBucket.listKeys() }
            .isInstanceOf(ArtworkStorageException::class.java)

        val peopleOnCatalogueBucket = MinioArtworkStore(peopleClient, CATALOGUE_BUCKET)
        assertThatThrownBy { peopleOnCatalogueBucket.put(ByteArrayInputStream(byteArrayOf(6)), "png") }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { peopleOnCatalogueBucket.open(seededInCatalogueBucket.storageKey) }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { peopleOnCatalogueBucket.delete(seededInCatalogueBucket.storageKey) }
            .isInstanceOf(ArtworkStorageException::class.java)
        assertThatThrownBy { peopleOnCatalogueBucket.listKeys() }
            .isInstanceOf(ArtworkStorageException::class.java)
    }
}
