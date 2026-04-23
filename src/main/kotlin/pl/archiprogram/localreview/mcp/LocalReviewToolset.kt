package pl.archiprogram.localreview.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.currentCoroutineContext
import pl.archiprogram.localreview.ui.SafeRefresh

/**
 * MCP toolset exposing Local Review's viewed-state to external AI agents via the bundled
 * IntelliJ MCP Server (com.intellij.mcpServer).
 *
 * Each tool:
 *  - checks [mcpToolsEnabled] first and throws via [noToolPermitted] if the user disabled the
 *    integration in Settings → Tools → Local Review;
 *  - resolves the current project from the coroutine context (the bundled MCP infrastructure
 *    populates the project extension before dispatching);
 *  - wraps the real work in a [runReadAction] since it touches VCS and VFS state.
 *
 * Tools return plain [String]s — list-shaped results are minimal JSON so agents can parse them
 * deterministically. The `internal` logic functions in LocalReviewMcpLogic are directly
 * unit-tested so we don't need a running platform / coroutine infrastructure in most tests.
 */
class LocalReviewToolset : McpToolset {
    @McpTool(name = "local_review_list_changes")
    @McpDescription(
        "List files in the current local changeset with their viewed status. Returns a JSON " +
            "array of { path, viewed, status } entries. `status` is one of MODIFIED, NEW, " +
            "DELETED, MERGED_WITH_CONFLICTS, OBSOLETE, NOT_CHANGED, UNKNOWN.",
    )
    suspend fun local_review_list_changes(): String {
        if (!mcpToolsEnabled()) noToolPermitted()
        val project = currentProject()
        return runReadAction {
            changeEntriesToJson(listChanges(project))
        }
    }

    @McpTool(name = "local_review_mark_all_viewed")
    @McpDescription(
        "Mark every file in the current local changeset as viewed. Skips files already viewed " +
            "and files in a merge conflict. Returns a human-readable message with the count " +
            "of files newly marked.",
    )
    suspend fun local_review_mark_all_viewed(): String {
        if (!mcpToolsEnabled()) noToolPermitted()
        val project = currentProject()
        return try {
            val marked = runReadAction { markAllViewed(project) }
            "Marked $marked file(s) as viewed."
        } finally {
            SafeRefresh.refreshFileStatuses(project)
            SafeRefresh.scheduleChangesViewRefresh(project)
        }
    }

    @McpTool(name = "local_review_unmark_all")
    @McpDescription(
        "Remove every 'viewed' mark for this project. Returns a human-readable message with " +
            "the count of files whose mark was cleared.",
    )
    suspend fun local_review_unmark_all(): String {
        if (!mcpToolsEnabled()) noToolPermitted()
        val project = currentProject()
        val removed = unmarkAll(project)
        SafeRefresh.refreshFileStatuses(project)
        SafeRefresh.scheduleChangesViewRefresh(project)
        return "Unmarked $removed file(s)."
    }

    @McpTool(name = "local_review_mark_files")
    @McpDescription(
        "Mark specific files as viewed. A file is only marked if it's part of the current " +
            "local changeset (matching the UI's 'Mark as Reviewed' semantics). Returns a JSON " +
            "array of { path, result } where result is 'marked', 'already_viewed', " +
            "'not_a_current_change', 'not_found', 'is_directory', 'blank_path', 'outside_vcs', " +
            "or 'hash_unavailable'.",
    )
    suspend fun local_review_mark_files(
        @McpDescription("Absolute or project-relative file paths to mark.")
        paths: List<String>,
    ): String {
        if (!mcpToolsEnabled()) noToolPermitted()
        val project = currentProject()
        return try {
            runReadAction {
                pathResultsToJson(markFiles(project, paths))
            }
        } finally {
            SafeRefresh.refreshFileStatuses(project)
            SafeRefresh.scheduleChangesViewRefresh(project)
        }
    }

    @McpTool(name = "local_review_unmark_files")
    @McpDescription(
        "Remove the 'viewed' mark from specific files. Unlike mark_files, this works even if " +
            "the file is no longer part of the current changeset. Returns a JSON array of " +
            "{ path, result } where result is 'unmarked', 'not_viewed', 'not_found', " +
            "'is_directory', 'blank_path', or 'outside_vcs'.",
    )
    suspend fun local_review_unmark_files(
        @McpDescription("Absolute or project-relative file paths to unmark.")
        paths: List<String>,
    ): String {
        if (!mcpToolsEnabled()) noToolPermitted()
        val project = currentProject()
        val result = pathResultsToJson(unmarkFiles(project, paths))
        SafeRefresh.refreshFileStatuses(project)
        SafeRefresh.scheduleChangesViewRefresh(project)
        return result
    }

    private suspend fun currentProject(): Project = currentCoroutineContext().project
}

/**
 * Signal "tool refused" to the MCP runtime. Returns [Nothing] so callers can use it in
 * expression position without Kotlin flow-analysis errors.
 *
 * We avoid the bundled plugin's fail helper so the refusal path is easy to unit-test: a plain
 * [IllegalStateException] is synchronous and doesn't require the MCP infrastructure.
 */
internal fun noToolPermitted(): Nothing = throw IllegalStateException(DISABLED_MESSAGE)
