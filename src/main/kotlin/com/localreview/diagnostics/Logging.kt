package com.localreview.diagnostics

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry

object Logging {

    @PublishedApi
    internal val LOG: Logger = Logger.getInstance("#com.localreview")

    inline fun trace(message: () -> String) {
        if (isEnabled()) {
            LOG.info(message())
        }
    }

    fun isEnabled(): Boolean =
        try {
            Registry.`is`("com.localreview.trace", false)
        } catch (_: Throwable) {
            false
        }
}
