plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":character"))
    testImplementation(kotlin("test"))
    testImplementation(libs.json.schema.validator)
}

application {
    mainClass = "dev.chieppa.squash.schemagen.MainKt"
}

val schemaDir = rootProject.layout.projectDirectory.dir("schema")
val templatesDir = rootProject.layout.projectDirectory.dir("templates")

tasks.register<JavaExec>("generateSchemas") {
    group = "squash"
    description = "Writes the JSON Schemas under schema/ from the Kotlin model."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(schemaDir.asFile.path)
}

tasks.test {
    useJUnitPlatform()
    inputs.dir(schemaDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(templatesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.schema", schemaDir.asFile.path)
    systemProperty("squash.templates", templatesDir.asFile.path)
}
