import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.app.sharedUI)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "dev.tireless.abun.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.tireless.abun"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        jvmArgs("-Dabun.debug=true")
    }
}
