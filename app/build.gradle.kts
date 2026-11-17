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
    implementation(project(":screens-dress"))
    implementation(project(":field-gpu"))
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
    // The examples' surfaces mesh their digits at a quarter of the body's cell; the suite's sessions hold them past the default heap.
    maxHeapSize = "1g"
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
        // -Popen=<file> opens it in the workspace at launch, in -Pstage=<STAGE> with -Pselect=<part> selected; -Preads=gpu reads fields on the GPU.
        // A relative -Popen path is read from the project's root, not from this module's folder the app runs in.
        providers.gradleProperty("open").orNull?.let { jvmArgs += "-Dsquash.open=${rootProject.file(it).path}" }
        listOf("stage", "select", "reads").forEach { name -> providers.gradleProperty(name).orNull?.let { jvmArgs += "-Dsquash.$name=$it" } }
        nativeDistributions {
            packageName = "Squash"
            packageVersion = project.version.toString().substringBefore('-')
            modules("java.instrument", "jdk.httpserver", "jdk.unsupported")
        }
    }
}

// ./gradlew :app:look -Plook="example=woman-dressed outfits=all poses=all close=face,hand,foot" renders examples to app/build/look;
// ./gradlew :app:measure -Pmeasure="example=woman-dressed" prints their checks. Both run the real graph with no test runner.
// ./gradlew :app:compare -Pcompare="example=woman-twin-dressed reference=<folder>" sets look's shots beside reference renders.
// ./gradlew :app:drag -Pdrag="example=fennec-dressed outfits=winter drags=move:front-leg-l" times scripted drags through the app's graph.
listOf("look" to "LookMainKt", "measure" to "MeasureMainKt", "compare" to "compare.CompareMainKt", "drag" to "drag.DragMainKt", "tris" to "TrisMainKt").forEach { (name, main) ->
    tasks.register<JavaExec>(name) {
        group = "verification"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "dev.chieppa.squash.screens.look.$main"
        javaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt())) }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        systemProperty("squash.templates", rootProject.layout.projectDirectory.dir("templates").asFile.path)
        args(listOf("root=${rootProject.layout.projectDirectory.asFile.path}") + (providers.gradleProperty(name).orNull?.split(' ') ?: emptyList()))
    }
}
