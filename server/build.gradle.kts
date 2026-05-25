plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "dev.tireless.abun"
version = "1.0.0"
application {
    mainClass = "dev.tireless.abun.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.hikari)
    implementation(libs.logback)
    implementation(libs.postgresql)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.h2)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.clientJson)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
