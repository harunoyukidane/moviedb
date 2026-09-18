package com.moviecatalogue.people.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort

class OffsetPageRequestTest {

    @Test
    fun `preserves an offset that is not a multiple of page size`() {
        val request = OffsetPageRequest(20, 5, Sort.by("name"))

        assertThat(request.offset).isEqualTo(5)
        assertThat(request.pageSize).isEqualTo(20)
        assertThat(request.pageNumber).isZero()
        assertThat(request.sort.getOrderFor("name")).isNotNull()
    }

    @Test
    fun `navigation retains offset semantics`() {
        val request = OffsetPageRequest(20, 25)

        assertThat(request.next().offset).isEqualTo(45)
        assertThat(request.previousOrFirst().offset).isEqualTo(5)
        assertThat(request.first().offset).isZero()
        assertThat(request.withPage(3).offset).isEqualTo(60)
    }
}
