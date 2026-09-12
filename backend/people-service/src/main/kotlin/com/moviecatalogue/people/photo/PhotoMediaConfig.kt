package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.LocalArtworkStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Wires the shared media components for person-photo upload (ADR-12 hybrid).
 * Reuses the exact same validated ArtworkStore path as movie artwork. Photos are
 * written to the People service's own artwork volume subtree.
 */
@Configuration
class PhotoMediaConfig {

    @Bean
    fun artworkStore(@Value("\${artwork.storage-path:./data/person-artwork}") path: String): ArtworkStore =
        LocalArtworkStore(Path.of(path))

    @Bean
    fun imageContentValidator(): ImageContentValidator = ImageContentValidator()
}
