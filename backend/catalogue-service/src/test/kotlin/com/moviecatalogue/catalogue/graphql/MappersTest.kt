package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.people.PersonData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class PersonDataMapperTest {

    private fun person(profilePath: String?) = PersonData(
        id = UUID.randomUUID(),
        tmdbId = null,
        name = "Someone",
        biography = "",
        birthDate = null,
        deathDate = null,
        placeOfBirth = null,
        profilePath = profilePath,
        version = 0,
    )

    @Test
    fun `photoUrl is the same-origin proxy path when a photo exists`() {
        val data = person(profilePath = "some-storage-key")
        assertThat(data.toGql().photoUrl).isEqualTo("/api/people/${data.id}/photo")
    }

    @Test
    fun `photoUrl is null when the person has no photo`() {
        val data = person(profilePath = null)
        assertThat(data.toGql().photoUrl).isNull()
    }
}
