package pl.archiprogram.localreview.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Resolves the current branc                               h for a VCS root. Default implementation returns [BranchProvider.NO_BRANCH].
 * The Git fragment registers an override that talks to Git4Idea.
 */
interface BranchProvider {
    fun currentBranch(
        project: Project,
        repoRoot: VirtualFile,
    ): String

    companion object {
        fun getInstance(): BranchProvider = ApplicationManager.getApplication().service()
    }
}
