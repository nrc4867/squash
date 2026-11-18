package dev.chieppa.squash.screens

import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.allStringArrayResources
import dev.chieppa.squash.uidesign.generated.allStringResources
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Loads every string of the app's resources once per test JVM, before its first test class. The resource library loads a string
 * on its first read on `Dispatchers.Default`, outside a test's [TestWork], so an action that reads one, as New garment reads the
 * names it gives, would finish on that thread's time; once loaded, every read completes within the test's own work. JUnit finds it
 * through extension autodetection, as it finds [TestWorkCloser].
 */
class StringsLoaded : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) = loaded

    private companion object {
        val loaded: Unit by lazy {
            runBlocking {
                Res.allStringResources.values.forEach { getString(it) }
                Res.allStringArrayResources.values.forEach { getStringArray(it) }
            }
        }
    }
}
