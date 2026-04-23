package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.ui.SimpleTextAttributes
import pl.archiprogram.localreview.Icons
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.vcs.ReviewBreakdown

/**
 * Adds a synthetic "Reviewed (N)" top-level group to the Local Changes tree. Files appear
 * both in their normal position and (duplicated) in this group so you can scan viewed files
 * at a glance.
 *
 * The ChangesViewModifier API only supports adding nodes, not decorating existing ones —
 * hence the duplication trade-off.
 */
class ReviewedChangesViewModifier(private val project: Project) : ChangesViewModifier {
    override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
        if (project.isDisposed) return
        val breakdown = ReviewBreakdown.compute(project)
        val totalCount = breakdown.totalCount
        LOG.info(
            "LocalReview: synthetic groups — viewed=${breakdown.viewedCount}, " +
                "unviewed=${breakdown.unviewedCount}, total=$totalCount",
        )

        if (totalCount == 0) return

        // Top: "To review (N)" so unviewed files are scannable in one place.
        if (breakdown.unviewedCount > 0) {
            val toReviewRoot = ToReviewRootNode(breakdown.unviewedCount)
            builder.insertSubtreeRoot(toReviewRoot)
            if (breakdown.unviewedChanges.isNotEmpty()) {
                builder.insertChanges(breakdown.unviewedChanges, toReviewRoot)
            }
            if (breakdown.unviewedUnversioned.isNotEmpty()) {
                builder.insertFilesIntoNode(breakdown.unviewedUnversioned, toReviewRoot)
            }
        }

        // Below: "Reviewed N of M · XX%" — progress + list of viewed files.
        if (breakdown.viewedCount > 0) {
            val reviewedRoot = ReviewedRootNode(viewedCount = breakdown.viewedCount, totalCount = totalCount)
            builder.insertSubtreeRoot(reviewedRoot)
            if (breakdown.viewedChanges.isNotEmpty()) {
                builder.insertChanges(breakdown.viewedChanges, reviewedRoot)
            }
            if (breakdown.viewedUnversioned.isNotEmpty()) {
                builder.insertFilesIntoNode(breakdown.viewedUnversioned, reviewedRoot)
            }
        }
    }

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ReviewedChangesViewModifier::class.java)
    }
}

class ReviewedRootNode(
    private val viewedCount: Int,
    private val totalCount: Int,
) : ChangesBrowserNode<String>(LABEL) {
    init {
        markAsHelperNode()
    }

    override fun render(
        renderer: ChangesBrowserNodeRenderer,
        selected: Boolean,
        expanded: Boolean,
        hasFocus: Boolean,
    ) {
        renderer.icon = Icons.VIEWED
        renderer.append(
            LocalReviewBundle.message("grouping.reviewed.label") + " ",
            SimpleTextAttributes.REGULAR_ATTRIBUTES,
        )
        renderer.append(
            "$viewedCount of $totalCount",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
        )
        val pct = if (totalCount > 0) (viewedCount * 100 / totalCount) else 0
        renderer.append(
            "  ·  $pct%",
            SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES,
        )
    }

    override fun getTextPresentation(): String = "${LocalReviewBundle.message("grouping.reviewed.label")} $viewedCount of $totalCount"

    companion object {
        private const val LABEL = "localreview.reviewed"
    }
}

class ToReviewRootNode(private val remaining: Int) : ChangesBrowserNode<String>(LABEL) {
    init {
        markAsHelperNode()
    }

    override fun render(
        renderer: ChangesBrowserNodeRenderer,
        selected: Boolean,
        expanded: Boolean,
        hasFocus: Boolean,
    ) {
        renderer.icon = Icons.UNVIEWED
        renderer.append(
            LocalReviewBundle.message("grouping.pending.label") + " ",
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
        )
        renderer.append("($remaining)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun getTextPresentation(): String = "${LocalReviewBundle.message("grouping.pending.label")} ($remaining)"

    companion object {
        private const val LABEL = "localreview.toreview"
    }
}
