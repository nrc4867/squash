import dev.chieppa.squash.build.WebgpuNatives

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    api(project(":geom"))
    implementation("dev.chieppa.stretch:stretch-gpu")
    runtimeOnly(WebgpuNatives.dependency(dependencies, providers))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
