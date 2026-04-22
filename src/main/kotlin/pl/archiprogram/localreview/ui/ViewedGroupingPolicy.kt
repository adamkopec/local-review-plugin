package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.SimpleChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.StaticFilePath
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.vcs.KeyDeriver
import javax.swing.tree.DefaultTreeModel

/** Value object used as group key so we don't clash with built-in policies that also use `String`. */
data class ViewedBucket(val viewed: Boolean)

class ViewedGroupingPolicy(
    private val project: Project,
    model: DefaultTreeModel,
) : SimpleChangesGroupingPolicy<ViewedBucket>(model) {

    override fun getGroupRootValueFor(
        nodePath: StaticFilePath,
        node: ChangesBrowserNode<*>,
    ): ViewedBucket? {
        val userObject = node.userObject as? Change ?: return null
        val file = userObject.afterRevision?.file ?: userObject.beforeRevision?.file ?: return null
        val key = KeyDeriver.keyFor(project, file) ?: return null
        val viewed = ReviewStateService.getInstance(project).isViewed(key)
        return ViewedBucket(viewed)
    }

    override fun createGroupRootNode(value: ViewedBucket): ChangesBrowserNode<*> {
        val node = ViewedGroupNode(value.viewed)
        node.markAsHelperNode()
        return node
    }
}
