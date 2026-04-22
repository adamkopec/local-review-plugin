package pl.archiprogram.localreview

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.LocalReviewBundle"

object LocalReviewBundle : DynamicBundle(BUNDLE_NAME) {

    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
        vararg params: Any,
    ): String = getMessage(key, *params)
}
