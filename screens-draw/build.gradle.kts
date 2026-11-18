plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    api(project(":screens-core"))

    testImplementation(kotlin("test"))
    testFixturesApi(testFixtures(project(":screens-core")))
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 2
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // The Move tests open the shipped fennec, whose surface meshes its digits at a quarter of the body's cell, past the default heap.
    maxHeapSize = "1g"
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
}
