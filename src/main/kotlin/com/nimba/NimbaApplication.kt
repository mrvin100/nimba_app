package com.nimba

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic
@SpringBootApplication
@ConfigurationPropertiesScan
class NimbaApplication

fun main(args: Array<String>) {
    runApplication<NimbaApplication>(*args)
}
