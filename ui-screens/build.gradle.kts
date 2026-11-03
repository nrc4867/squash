plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    api(project(":ui-design"))
    api(project(":render"))
    api(project(":character"))
    api("dev.chieppa.stretch:stretch-frame")
    api("dev.chieppa.stretch:stretch-ui")
    testImplementation(kotlin("test"))
    testImplementation("dev.chieppa.stretch:stretch-test")
    testImplementation(compose.desktop.currentOs)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
}
