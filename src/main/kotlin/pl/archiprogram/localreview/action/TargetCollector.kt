package pl.archiprogram.localreview.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.vcs.KeyDeriver

/**
 * Pure orchestration of "what should we apply the action to?" given the three data-key
 * shapes the platform hands us. Split out of [ToggleViewedAction] so it can be unit-tested
 * without a live `AnActionEvent` / Swing wiring.
 */
internal object TargetCollector {
    fun collect(
        project: Project,
        changes: Array<Change>?,
        unversionedPaths: Iterable<FilePath>?,
        candidateFiles: List<VirtualFile>,
    ): List<Target> {
        val clm = ChangeListManager.getInstance(project)
        val out = mutableListOf<Target>()
        val seen = mutableSetOf<Key>()

        changes?.forEach { change ->
            val key = change.key(project) ?: return@forEach
            if (seen.add(key)) out += Target.Changed(project, change, key)
        }

        unversionedPaths?.forEach { filePath ->
            val key = KeyDeriver.keyFor(project, filePath) ?: return@forEach
            if (seen.add(key)) out += Target.Unversioned(project, filePath, key)
        }

        for (vf in candidateFiles) {
            if (vf.isDirectory || !vf.isValid) continue
            val change = clm.getChange(vf)
            val unversioned = clm.isUnversioned(vf)
            if (change == null && !unversioned) continue
            val filePath = VcsUtil.getFilePath(vf)
            val key = KeyDeriver.keyFor(project, filePath) ?: continue
            if (!seen.add(key)) continue
            out +=
                if (change != null) {
                    Target.Changed(project, change, key)
                } else {
                    Target.Unversioned(project, filePath, key)
                }
        }

        return out
    }
}
