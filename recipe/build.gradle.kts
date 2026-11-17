plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    api(project(":character"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    val templates = rootProject.layout.projectDirectory.dir("templates")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.templates", templates.asFile.path)
    val recipes = rootProject.layout.projectDirectory.dir("recipes")
    inputs.dir(recipes).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.recipes", recipes.asFile.path)
}
