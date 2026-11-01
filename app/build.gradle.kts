plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation("dev.chieppa.stretch:stretch-ui")
    implementation("dev.chieppa.stretch:stretch-core")
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "dev.chieppa.squash.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt()))
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
    }
}
