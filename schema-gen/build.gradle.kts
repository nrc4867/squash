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
    implementation(project(":recipe"))
    implementation(project(":rig"))
    testImplementation(kotlin("test"))
    testImplementation(libs.json.schema.validator)
}

application {
    mainClass = "dev.chieppa.squash.schemagen.MainKt"
}

val schemaDir = rootProject.layout.projectDirectory.dir("schema")
val templatesDir = rootProject.layout.projectDirectory.dir("templates")
val recipesDir = rootProject.layout.projectDirectory.dir("recipes")
val referenceDir = rootProject.layout.projectDirectory.dir("docs/reference")

tasks.register<JavaExec>("generateSchemas") {
    group = "squash"
    description = "Writes the JSON Schemas under schema/ from the Kotlin model, and the recipe reference under docs/reference/."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(schemaDir.asFile.path, referenceDir.asFile.path)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.dir(schemaDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(templatesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.schema", schemaDir.asFile.path)
    systemProperty("squash.templates", templatesDir.asFile.path)
    inputs.dir(recipesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.recipes", recipesDir.asFile.path)
    inputs.dir(referenceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("squash.reference", referenceDir.asFile.path)
}

// The kit's TypeScript recipe types follow schema/recipe.schema.json: generateSchemas rewrites them, check fails when stale.
val kitTypesTool = rootProject.layout.projectDirectory.file("kit/tools/gen-types.ts")

val generateKitTypes = tasks.register<Exec>("generateKitTypes") {
    group = "squash"
    description = "Writes kit/src/schema/recipe.ts from schema/recipe.schema.json."
    commandLine("node", kitTypesTool.asFile.path)
}
tasks.named("generateSchemas") { finalizedBy(generateKitTypes) }

val checkKitTypes = tasks.register<Exec>("checkKitTypes") {
    group = "verification"
    description = "Fails when kit/src/schema/recipe.ts differs from what schema/recipe.schema.json generates."
    commandLine("node", kitTypesTool.asFile.path, "--check")
}
tasks.named("check") { dependsOn(checkKitTypes) }
tasks.named("checkFast") { dependsOn(checkKitTypes) }
