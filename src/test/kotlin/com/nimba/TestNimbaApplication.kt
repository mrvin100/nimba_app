package com.nimba

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<NimbaApplication>().with(TestcontainersConfiguration::class).run(*args)
}
