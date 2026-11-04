plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":screens-shell"))
    implementation(project(":screens-sheet"))
    implementation(project(":screens-draw"))
    implementation(project(":screens-surface"))
    implementation("dev.chieppa.stretch:stretch-core")
    implementation("dev.chieppa.stretch:stretch-theme")
    implementation("dev.chieppa.stretch:stretch-ui")
    implementation("dev.chieppa.stretch:stretch-frame")
    implementation("dev.chieppa.stretch:stretch-document")
    implementation("dev.chieppa.stretch:stretch-devtools")
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":screens-core")))
    testImplementation(testFixtures(project(":screens-draw")))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
    // -PlookProbe overwrites the committed look probe images, which a run otherwise leaves alone.
    if (providers.gradleProperty("lookProbe").isPresent) {
        systemProperty("squash.lookProbeImages", rootProject.layout.projectDirectory.dir("docs/work/look-probe").asFile.path)
        outputs.upToDateWhen { false }
    }
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
        // -Pdevtools=<port> serves screenshots of the windows on 127.0.0.1.
        providers.gradleProperty("devtools").orNull?.let { jvmArgs += "-Dstretch.devtools=$it" }
        nativeDistributions {
            packageName = "Squash"
            packageVersion = project.version.toString().substringBefore('-')
            modules("java.instrument", "jdk.httpserver", "jdk.unsupported")
        }
    }
}
