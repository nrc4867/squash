import dev.chieppa.squash.build.GenerateIcons

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api("dev.chieppa.stretch:stretch-theme")
            api(libs.compose.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.chieppa.squash.uidesign.generated"
}

fun GenerateIcons.configure(verify: Boolean) {
    icons.set(rootProject.layout.projectDirectory.dir("design/icons"))
    target.set(layout.projectDirectory.file("src/commonMain/kotlin/dev/chieppa/squash/uidesign/SquashIcons.kt"))
    this.verify.set(verify)
}

tasks.register<GenerateIcons>("generateIcons") { group = "squash"; configure(verify = false) }
val verifyIcons = tasks.register<GenerateIcons>("verifyIcons") { group = "verification"; configure(verify = true) }

tasks.named("check") { dependsOn(verifyIcons) }
