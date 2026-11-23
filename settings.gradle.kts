rootProject.name = "squash"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Stretch's wgpu-native bindings, from GitHub Packages: credentials from GitHubPackagesUsername and
        // GitHubPackagesPassword (in ~/.gradle/gradle.properties, a token with read:packages; CI passes its own).
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/nrc4867/webgpu-native-ffm")
            credentials(PasswordCredentials::class)
            mavenContent { includeModule("dev.chieppa", "webgpu-native-ffm") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Stretch is built from source: the checkout named by stretch.dir (set it in ~/.gradle/gradle.properties),
// or a sibling folder named stretch.
val stretchDir = providers.gradleProperty("stretch.dir").orNull
    ?: file("../stretch").takeIf { it.isDirectory }?.path
    ?: error("Stretch not found: set stretch.dir in ~/.gradle/gradle.properties to your Stretch checkout")
includeBuild(stretchDir)

include(":annotations")
include(":geom")
include(":graph")
include(":document")
include(":character")
include(":rig")
include(":recipe")
include(":render")
include(":field-gpu")
include(":ui-design")
include(":screens-core")
include(":screens-shell")
include(":screens-sheet")
include(":screens-draw")
include(":screens-surface")
include(":screens-dress")
include(":app")
include(":schema-gen")
