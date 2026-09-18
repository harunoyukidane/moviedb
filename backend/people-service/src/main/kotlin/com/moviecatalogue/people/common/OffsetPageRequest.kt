package com.moviecatalogue.people.common

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * A Spring Data [Pageable] that preserves an arbitrary row offset (V2.5-02).
 * `PageRequest.of(page, size)` can only express page-aligned offsets
 * (offset = page * size); search/list offsets here are caller-supplied and
 * not guaranteed to be a multiple of the page size. Mirrors
 * catalogue-service's `OffsetPageRequest` (people-service and
 * catalogue-service don't share code, per ADR-1).
 */
class OffsetPageRequest(
    private val size: Int,
    private val start: Long,
    private val ordering: Sort = Sort.unsorted(),
) : Pageable {

    init {
        require(size > 0) { "page size must be positive" }
        require(start >= 0) { "offset must not be negative" }
    }

    override fun getPageNumber(): Int = (start / size).toInt()
    override fun getPageSize(): Int = size
    override fun getOffset(): Long = start
    override fun getSort(): Sort = ordering
    override fun next(): Pageable = OffsetPageRequest(size, start + size, ordering)
    override fun previousOrFirst(): Pageable =
        if (hasPrevious()) OffsetPageRequest(size, (start - size).coerceAtLeast(0), ordering) else first()
    override fun first(): Pageable = OffsetPageRequest(size, 0, ordering)
    override fun withPage(pageNumber: Int): Pageable {
        require(pageNumber >= 0) { "page number must not be negative" }
        return OffsetPageRequest(size, pageNumber.toLong() * size, ordering)
    }
    override fun hasPrevious(): Boolean = start > 0
}
