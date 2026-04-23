package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicy
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import javax.swing.tree.DefaultTreeModel

class ViewedGroupingPolicyFactory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(
        project: Project,
        model: DefaultTreeModel,
    ): ChangesGroupingPolicy = ViewedGroupingPolicy(project, model)
}
