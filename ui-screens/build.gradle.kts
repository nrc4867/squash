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
}
