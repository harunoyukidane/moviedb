package com.moviecatalogue.people

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PeopleServiceApplication

fun main(args: Array<String>) {
    runApplication<PeopleServiceApplication>(*args)
}
