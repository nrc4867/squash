import dev.chieppa.squash.build.CheckTestSources
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
            // A test that hangs fails on these instead of holding the build: each JUnit 5 test, and each module's run as a whole.
            systemProperty("junit.jupiter.execution.timeout.default", "5 m")
            timeout.set(java.time.Duration.ofMinutes(30))
        }
    }
}

// Every module's check reads its test sources for waits on the clock and reflection that widens visibility.
subprojects {
    val checkTestSources = tasks.register<CheckTestSources>("checkTestSources") {
        group = "verification"
        description = "Fails on Thread.sleep, delay, waitUntil or widened visibility in test sources."
        sources.from(listOf("test", "desktopTest", "commonTest", "testFixtures").map { layout.projectDirectory.dir("src/$it") })
        root.set(rootProject.layout.projectDirectory)
        report.set(layout.buildDirectory.file("reports/checkTestSources.txt"))
    }
    plugins.withType<LifecycleBasePlugin> { tasks.named("check") { dependsOn(checkTestSources) } }
}
