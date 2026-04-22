package pl.archiprogram.localreview.ui

import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import pl.archiprogram.localreview.Icons
import pl.archiprogram.localreview.LocalReviewBundle

class ViewedGroupNode(val viewed: Boolean) : ChangesBrowserNode<Boolean>(viewed) {

    override fun render(
        renderer: ChangesBrowserNodeRenderer,
        selected: Boolean,
        expanded: Boolean,
        hasFocus: Boolean,
    ) {
        val key = if (viewed) "grouping.reviewed.label" else "grouping.pending.label"
        val attrs = if (viewed) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
        renderer.append(LocalReviewBundle.message(key), attrs)
        appendCount(renderer)
        renderer.icon = if (viewed) Icons.VIEWED else Icons.UNVIEWED
    }

    override fun getTextPresentation(): String =
        LocalReviewBundle.message(
            if (viewed) "grouping.reviewed.label" else "grouping.pending.label",
        )
}
