package dev.chieppa.squash.build

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.ProviderFactory

/**
 * wgpu-native for the platform Squash is built and packaged for: this machine's, or the platform named by
 * `-Pwebgpu.natives` (`windows-x64`, `linux-x64`, `linux-arm64`, `macos-x64` or `macos-arm64`). Stretch's
 * `stretch-gpu` brings the bindings but not the library, and sets the version this jar follows.
 */
object WebgpuNatives {
    const val PROPERTY = "webgpu.natives"

    /** The natives jar of `dev.chieppa:webgpu-native-ffm`, at the version stretch-gpu resolves. */
    fun dependency(dependencies: DependencyHandler, providers: ProviderFactory): Any =
        dependencies.create(
            mapOf(
                "group" to "dev.chieppa",
                "name" to "webgpu-native-ffm",
                "classifier" to "natives-${providers.gradleProperty(PROPERTY).orNull ?: host()}",
            ),
        )

    /** The platform of the machine running the build. */
    fun host(): String {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
        return when {
            os.startsWith("windows") -> "windows-x64"
            os.startsWith("mac") -> if (arm) "macos-arm64" else "macos-x64"
            else -> if (arm) "linux-arm64" else "linux-x64"
        }
    }
}
