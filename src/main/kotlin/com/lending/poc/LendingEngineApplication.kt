package com.lending.poc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class LendingEngineApplication

fun main(args: Array<String>) {
    runApplication<LendingEngineApplication>(*args)
}
