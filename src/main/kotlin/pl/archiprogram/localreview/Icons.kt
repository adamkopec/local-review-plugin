package pl.archiprogram.localreview

import com.intellij.openapi.util.IconLoader

object Icons {
    @JvmField val VIEWED = IconLoader.getIcon("/icons/viewed.svg", Icons::class.java)
    @JvmField val UNVIEWED = IconLoader.getIcon("/icons/unviewed.svg", Icons::class.java)
}
