plugins {
    application
    id("org.springframework.boot") version "2.3.1.RELEASE"
}

application {
    mainClassName = "com.rafael.twitter.stream.controller.application.Application"
}

apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework:spring-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.github.kittinunf.result:result:3.0.1")
    implementation("com.github.kittinunf.fuel:fuel:2.2.3")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.2.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.github.tomakehurst:wiremock:2.27.1")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
}

tasks {
    bootJar {
        mainClassName = "com.rafael.twitter.stream.controller.application.Application"
    }

    test {
        useJUnitPlatform()
    }
}