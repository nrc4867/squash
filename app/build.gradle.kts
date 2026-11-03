plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":ui-screens"))
    implementation("dev.chieppa.stretch:stretch-core")
    implementation("dev.chieppa.stretch:stretch-theme")
    implementation("dev.chieppa.stretch:stretch-ui")
    implementation("dev.chieppa.stretch:stretch-frame")
    implementation("dev.chieppa.stretch:stretch-document")
    implementation("dev.chieppa.stretch:stretch-devtools")
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "dev.chieppa.squash.MainKt"
        // The JetBrains Runtime, so StretchWindow can draw its own title bar in a native frame.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt()))
            vendor.set(JvmVendorSpec.JETBRAINS)
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        jvmArgs += "-Dsquash.templates=${rootProject.layout.projectDirectory.dir("templates").asFile.path}"
        // -Pdevtools=<port> serves screenshots of the windows on 127.0.0.1.
        providers.gradleProperty("devtools").orNull?.let { jvmArgs += "-Dstretch.devtools=$it" }
    }
}
