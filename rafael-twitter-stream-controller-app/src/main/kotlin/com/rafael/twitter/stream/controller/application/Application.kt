package com.rafael.twitter.stream.controller.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.rafael.twitter.stream.controller.web"])
open class Application {

	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			runApplication<Application>(*args)
		}
	}
}
