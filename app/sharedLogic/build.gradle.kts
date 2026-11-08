import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    js {
        outputModuleName = "sharedLogic"
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target = "es2015"
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
    }
    
    androidLibrary {
       namespace = "dev.tireless.abun.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/buildInfo"))
        }
        commonMain.dependencies {
            api(projects.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.serialization.json)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiationCommon)
            implementation(libs.ktor.serializationKotlinxJsonCommon)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.clientMock)
            implementation(libs.sqldelight.sqliteDriver)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.clientOkHttp)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.shizuku.api)
        }
        iosMain.dependencies {
            implementation(libs.ktor.clientDarwin)
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.sqldelight.sqliteDriver)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.sqldelight.webDriver)
        }
    }
}

sqldelight {
    databases {
        create("AbunDatabase") {
            packageName.set("dev.tireless.abun.db")
        }
    }
}

abstract class GenerateBuildInfoTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val rootDirPath: Property<String>

    @TaskAction
    fun generate() {
        val now = LocalDateTime.now()
        val timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
        val root = File(rootDirPath.get())
        val commitHash = runCatching {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(root)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor()
            process.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("unknown")

        val isDirty = runCatching {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(root)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            process.waitFor()
            process.inputStream.bufferedReader().readText().isNotBlank()
        }.getOrDefault(false)

        val buildId = if (isDirty) "${timestamp}-wip" else "${timestamp}-${commitHash}"

        val file = outputDir.get().asFile.resolve("dev/tireless/abun/core/ota/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package dev.tireless.abun.core.ota

            object BuildInfo {
                const val VERSION_NAME: String = "0.0.0"
                const val BUILD_IDENTIFIER: String = "$buildId"
                const val IS_DEV_BUILD: Boolean = true
                const val OTA_CHECK_URL: String = "https://ota.tireless.dev/abun/latest.json"
            }
            """.trimIndent()
        )
    }
}

val generateBuildInfo = tasks.register<GenerateBuildInfoTask>("generateBuildInfo") {
    outputDir.set(layout.buildDirectory.dir("generated/source/buildInfo"))
    rootDirPath.set(rootDir.absolutePath)
    outputs.upToDateWhen { false }
}

tasks.matching { it.name.contains("compile", ignoreCase = true) || it.name.startsWith("pre") }.configureEach {
    dependsOn(generateBuildInfo)
}
