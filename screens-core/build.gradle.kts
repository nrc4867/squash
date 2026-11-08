plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

sourceSets.testFixtures { resources.srcDir(rootProject.layout.projectDirectory.dir("examples")) }

dependencies {
    api(project(":ui-design"))
    api(project(":render"))
    api(project(":character"))
    api(project(":rig"))
    api("dev.chieppa.stretch:stretch-frame")
    api("dev.chieppa.stretch:stretch-ui")
    api("dev.chieppa.stretch:stretch-document")

    testImplementation(kotlin("test"))
    testFixturesApi(kotlin("test"))
    testFixturesImplementation(kotlin("test-junit5"))
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi("dev.chieppa.stretch:stretch-test")
    testFixturesApi(compose.desktop.currentOs)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
    if (providers.gradleProperty("updateGoldens").isPresent) {
        systemProperty("squash.updateGoldens", "true")
        outputs.upToDateWhen { false }
    }
}
