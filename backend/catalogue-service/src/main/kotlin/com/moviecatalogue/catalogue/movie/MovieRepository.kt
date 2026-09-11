package com.moviecatalogue.catalogue.movie

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MovieRepository : JpaRepository<Movie, UUID>
