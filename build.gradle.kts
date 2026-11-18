import dev.chieppa.squash.build.CheckTestSources

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

// Test tiers, by JUnit 5 tag. Untagged tests are the fast tier: a module's test or desktopTest task runs them. Tests tagged
// integration build whole characters through the real graph; each module's integrationTest runs them, and check runs both. Tests
// tagged slow are the smoke tier and the benchmarks, which only -Pslow runs, inside whichever of the two tasks holds them.
val includeSlowTests = providers.gradleProperty("slow").isPresent
val integrationTag = "integration"
val slowTag = "slow"
val integrationTaskName = "integrationTest"

subprojects {
    // checkFast is check without the integration tier: every fast test task, the test source check and generated-file checks.
    val checkFast = tasks.register("checkFast") {
        group = "verification"
        description = "Runs the fast test tier and the source checks, without the integration tier."
    }
    // After evaluation, because a module's script is what makes its test task and chooses the JUnit Platform.
    afterEvaluate {
        val fast = tasks.findByName("desktopTest") as? Test ?: tasks.findByName("test") as? Test
        if (fast != null) {
            val integration = tasks.register<Test>(integrationTaskName) {
                group = "verification"
                description = "Runs the tests tagged $integrationTag: whole characters through the real graph."
                testClassesDirs = fast.testClassesDirs
                classpath = fast.classpath
                javaLauncher.set(fast.javaLauncher)
                shouldRunAfter(fast)
            }
            tasks.named("check") { dependsOn(integration) }
            checkFast { dependsOn(fast) }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform {
                if (name == integrationTaskName) includeTags(integrationTag) else excludeTags(integrationTag)
                if (!includeSlowTests) excludeTags(slowTag)
            }
            // A test that hangs fails on these instead of holding the build: each JUnit 5 test, and each module's run as a whole.
            systemProperty("junit.jupiter.execution.timeout.default", "5 m")
            // Extensions test fixtures list in META-INF/services, such as screens-core's TestWorkCloser, apply to every test.
            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
            // runTest's own limit matches JUnit's, so a test slowed by the forks beside it is not failed as hung.
            systemProperty("kotlinx.coroutines.test.default_timeout", "5m")
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
    plugins.withType<LifecycleBasePlugin> {
        tasks.named("check") { dependsOn(checkTestSources) }
        tasks.named("checkFast") { dependsOn(checkTestSources) }
    }
}
