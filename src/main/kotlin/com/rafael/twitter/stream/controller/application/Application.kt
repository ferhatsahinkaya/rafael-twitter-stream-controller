package com.rafael.twitter.stream.controller.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.rafael.twitter.stream.controller.web"])
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
