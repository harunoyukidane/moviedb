package com.moviecatalogue.catalogue.artwork

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** V2.6-06: q-value-aware Accept negotiation for the WebP variant. */
class AcceptsWebpTest {

    @Test
    fun `no Accept header falls back to the primary asset`() {
        assertThat(acceptsWebp(null)).isFalse()
    }

    @Test
    fun `a bare wildcard Accept falls back to the primary asset`() {
        assertThat(acceptsWebp("*/*")).isFalse()
    }

    @Test
    fun `an explicit image webp Accept is honoured`() {
        assertThat(acceptsWebp("image/webp")).isTrue()
        assertThat(acceptsWebp("image/webp,*/*;q=0.8")).isTrue()
    }

    @Test
    fun `an explicit q=0 refusal is honoured instead of matching the substring`() {
        assertThat(acceptsWebp("image/webp;q=0")).isFalse()
        assertThat(acceptsWebp("image/webp;q=0, */*;q=0.5")).isFalse()
    }

    @Test
    fun `a low but non-zero quality still counts as accepting`() {
        assertThat(acceptsWebp("image/webp;q=0.1")).isTrue()
    }

    @Test
    fun `an unparseable Accept header falls back to the primary asset`() {
        assertThat(acceptsWebp("not a media type;;;")).isFalse()
    }
}
