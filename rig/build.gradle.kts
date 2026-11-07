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
        commonMain.dependencies {
            api(project(":geom"))
            api(project(":graph"))
            api(project(":document"))
            api(project(":character"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    val examples = rootProject.layout.projectDirectory.dir("examples")
    inputs.dir(examples).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.examples", examples.asFile.path)
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
    if (providers.gradleProperty("updateGoldens").isPresent) {
        systemProperty("squash.updateGoldens", "true")
        outputs.upToDateWhen { false }
    }
}
