plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    jvm("desktop")

    sourceSets {
        named("desktopTest") {
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("geom/src/goldenSupport/kotlin"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("updateGoldens").isPresent) {
        systemProperty("squash.updateGoldens", "true")
        outputs.upToDateWhen { false }
    }
}
