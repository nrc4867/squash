import dev.chieppa.squash.build.PackTemplates

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val packTemplates = tasks.register<PackTemplates>("packTemplates") {
    group = "squash"
    description = "Copies the templates folder into the resources, with an index of its files."
    templates.set(rootProject.layout.projectDirectory.dir("templates"))
    root.set("dev/chieppa/squash/templates")
    indexName.set("index.txt")
    resources.set(layout.buildDirectory.dir("generated/templateResources"))
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            resources.srcDir(packTemplates.flatMap { it.resources })
        }
        named("desktopTest") {
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("geom/src/goldenSupport/kotlin"))
        }
        commonMain.dependencies {
            api(project(":annotations"))
            api(project(":geom"))
            api(project(":graph"))
            api(project(":document"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
}

tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("updateGoldens").isPresent) {
        systemProperty("squash.updateGoldens", "true")
        outputs.upToDateWhen { false }
    }
}
