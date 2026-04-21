package com.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.ui.SimpleTextAttributes
import com.localreview.Icons
import com.localreview.LocalReviewBundle
import com.localreview.state.ReviewStateService
import com.localreview.vcs.KeyDeriver

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
        val service = ReviewStateService.getInstance(project)
        val clm = ChangeListManager.getInstance(project)

        val viewedChanges = mutableListOf<com.intellij.openapi.vcs.changes.Change>()
        val unviewedChanges = mutableListOf<com.intellij.openapi.vcs.changes.Change>()
        for (change in clm.allChanges) {
            val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: continue
            val key = KeyDeriver.keyFor(project, file) ?: continue
            if (service.isViewed(key)) viewedChanges += change else unviewedChanges += change
        }

        val viewedUnversioned = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        val unviewedUnversioned = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        for (filePath in clm.unversionedFilesPaths) {
            val vf = filePath.virtualFile ?: continue
            val key = KeyDeriver.keyFor(project, filePath) ?: continue
            if (service.isViewed(key)) viewedUnversioned += vf else unviewedUnversioned += vf
        }

        val viewedCount = viewedChanges.size + viewedUnversioned.size
        val unviewedCount = unviewedChanges.size + unviewedUnversioned.size
        val totalCount = viewedCount + unviewedCount
        LOG.info("LocalReview: synthetic groups — viewed=$viewedCount, unviewed=$unviewedCount, total=$totalCount")

        if (totalCount == 0) return

        // Top: "To review (N)" so unviewed files are scannable in one place.
        if (unviewedCount > 0) {
            val toReviewRoot = ToReviewRootNode(unviewedCount)
            builder.insertSubtreeRoot(toReviewRoot)
            if (unviewedChanges.isNotEmpty()) builder.insertChanges(unviewedChanges, toReviewRoot)
            if (unviewedUnversioned.isNotEmpty()) builder.insertFilesIntoNode(unviewedUnversioned, toReviewRoot)
        }

        // Below: "Reviewed N of M · XX%" — progress + list of viewed files.
        if (viewedCount > 0) {
            val reviewedRoot = ReviewedRootNode(viewedCount = viewedCount, totalCount = totalCount)
            builder.insertSubtreeRoot(reviewedRoot)
            if (viewedChanges.isNotEmpty()) builder.insertChanges(viewedChanges, reviewedRoot)
            if (viewedUnversioned.isNotEmpty()) builder.insertFilesIntoNode(viewedUnversioned, reviewedRoot)
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
            LocalReviewBundle.message("grouping.viewed.bucket.viewed") + " ",
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

    override fun getTextPresentation(): String =
        "${LocalReviewBundle.message("grouping.viewed.bucket.viewed")} $viewedCount of $totalCount"

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
            LocalReviewBundle.message("grouping.toreview.title") + " ",
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
        )
        renderer.append("($remaining)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun getTextPresentation(): String =
        "${LocalReviewBundle.message("grouping.toreview.title")} ($remaining)"

    companion object {
        private const val LABEL = "localreview.toreview"
    }
}
