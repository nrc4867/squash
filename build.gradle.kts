import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "dev.chieppa.squash"
    version = "0.1.0-SNAPSHOT"
}

// Tests tagged slow are the smoke tier, which only -Pslow runs. After evaluation, because a module's script is what
// chooses the JUnit Platform.
val includeSlowTests = providers.gradleProperty("slow").isPresent

subprojects {
    afterEvaluate {
        tasks.withType<Test>().configureEach {
            if (!includeSlowTests) (options as? JUnitPlatformOptions)?.excludeTags("slow")
        }
    }
}
