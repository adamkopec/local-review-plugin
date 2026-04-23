package pl.archiprogram.localreview.diagnostics

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry

object Logging {
    @PublishedApi
    internal val LOG: Logger = Logger.getInstance("#pl.archiprogram.localreview")

    inline fun trace(message: () -> String) {
        if (isEnabled()) {
            LOG.info(message())
        }
    }

    fun isEnabled(): Boolean =
        try {
            Registry.`is`("pl.archiprogram.localreview.trace", false)
        } catch (_: Throwable) {
            false
        }
}
