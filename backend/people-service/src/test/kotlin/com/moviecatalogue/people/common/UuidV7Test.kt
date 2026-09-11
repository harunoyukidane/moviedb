package com.moviecatalogue.people.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UuidV7Test {

    @Test
    fun `generates valid version 7 uuids`() {
        val uuid = UuidV7.generate()
        assertThat(uuid.version()).isEqualTo(7)
        // canonical string form
        assertThat(uuid.toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    @Test
    fun `generates distinct values`() {
        val values = (1..1000).map { UuidV7.generate() }.toSet()
        assertThat(values).hasSize(1000)
    }

    @Test
    fun `values are time-ordered (monotonic non-decreasing)`() {
        val a = UuidV7.generate()
        Thread.sleep(2)
        val b = UuidV7.generate()
        // UUIDv7 sorts lexicographically by time prefix.
        assertThat(a.toString() < b.toString()).isTrue()
    }
}
