val kotlinLoggingVersion = "7.0.13"
val logbackVersion = "1.5.18"
val ktorVersion = "3.3.0"
val koinVersion = "4.1.1"
val mockkVersion = "1.14.5"
val coroutinesVersion = "1.10.2"
val kotestVersion = "6.0.3"
val mongodbVersion = "5.6.0"

plugins {
    application
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    kotlin("jvm") version "2.2.0"
    id("io.ktor.plugin") version "3.3.0"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "com.svebrant"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.oshai", "kotlin-logging-jvm", kotlinLoggingVersion)
    implementation("ch.qos.logback", "logback-classic", logbackVersion)

    implementation("io.ktor", "ktor-server-core", ktorVersion)
    implementation("io.ktor", "ktor-server-config-yaml", ktorVersion)
    implementation("io.ktor", "ktor-server-netty", ktorVersion)
    implementation("io.ktor", "ktor-server-compression", ktorVersion)
    implementation("io.ktor", "ktor-serialization-kotlinx-json", ktorVersion)
    implementation("io.ktor", "ktor-server-content-negotiation", ktorVersion)
    implementation("io.ktor", "ktor-server-call-logging", ktorVersion)
    implementation("io.ktor", "ktor-server-auth", ktorVersion)

    implementation("io.insert-koin", "koin-core", koinVersion)
    implementation("io.insert-koin", "koin-ktor", koinVersion)

    implementation("org.mongodb", "mongodb-driver-kotlin-coroutine", mongodbVersion)
    implementation("org.mongodb", "mongodb-driver-kotlin-extensions", mongodbVersion)
    implementation("org.mongodb", "bson-kotlinx", mongodbVersion)
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.0")
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", "0.6.1")

    testImplementation(kotlin("test"))
    testImplementation("io.insert-koin", "koin-test", koinVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
    testImplementation("io.kotest", "kotest-assertions-core-jvm", kotestVersion)
    testImplementation("io.kotest", "kotest-runner-junit5-jvm", kotestVersion)
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", coroutinesVersion)
}

tasks.test {
    useJUnitPlatform()
}
