package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import pl.archiprogram.localreview.state.Key

object KeyDeriver {
    /** Derive a [Key] for a [VirtualFile] under VCS, or null if the file is outside any VCS root. */
    fun keyFor(
        project: Project,
        file: VirtualFile,
    ): Key? {
        val vcsRoot =
            ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file)
                ?: return keyForNoVcs(file)
        val branch =
            try {
                BranchProvider.getInstance().currentBranch(project, vcsRoot)
            } catch (_: Throwable) {
                Key.NO_BRANCH
            }
        return Key(
            repoRoot = vcsRoot.path,
            branch = branch,
            path = file.path,
        )
    }

    /** Derive a [Key] for a [FilePath] — used when we only have path info (deleted files). */
    fun keyFor(
        project: Project,
        filePath: FilePath,
    ): Key? {
        val vf = filePath.virtualFile
        if (vf != null) return keyFor(project, vf)

        val vcsRoot =
            ProjectLevelVcsManager.getInstance(project)
                .getVcsRootFor(filePath) ?: return null
        val branch =
            try {
                BranchProvider.getInstance().currentBranch(project, vcsRoot)
            } catch (_: Throwable) {
                Key.NO_BRANCH
            }
        return Key(
            repoRoot = vcsRoot.path,
            branch = branch,
            path = filePath.path,
        )
    }

    private fun keyForNoVcs(file: VirtualFile): Key = Key(repoRoot = Key.NO_VCS, branch = Key.NO_BRANCH, path = file.path)
}
