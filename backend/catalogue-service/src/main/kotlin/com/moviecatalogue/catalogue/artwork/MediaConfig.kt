package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.LocalArtworkStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path

/**
 * Wires the shared media components (§10). The artwork volume path comes from
 * config so Compose can mount a managed volume (ARTWORK_STORAGE_PATH).
 */
@Configuration
class MediaConfig {

    @Bean
    fun artworkStore(@Value("\${artwork.storage-path:./data/artwork}") path: String): ArtworkStore =
        LocalArtworkStore(Path.of(path))

    @Bean
    fun imageContentValidator(): ImageContentValidator = ImageContentValidator()

    @Bean
    fun artworkTransactionTemplate(txManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(txManager)
}
