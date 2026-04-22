package pl.archiprogram.localreview.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.settings.LocalReviewSettings
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

/**
 * Contract tests for the reflection-scanned [LocalReviewToolset]:
 *  - implements [McpToolset] so the IDE's ReflectionToolsProvider discovers it.
 *  - exposes exactly the five promised `local_review_*` tool names as `suspend fun`s — these
 *    are the stable contract with user prompts.
 *  - every tool entry point refuses (throws) when the user has disabled the integration.
 *
 * Thick logic paths are covered in [LocalReviewMcpLogicTest]; this file covers only the toolset
 * wrapper's declarative + gate behavior.
 */
class LocalReviewToolsetTest {

    private val settings: LocalReviewSettings = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        // NB: don't mockkStatic(ApplicationManager::class) here. The platform spins up
        // background coroutines (e.g. ShadeIndexDumbModeTracker on 2024.2) that call
        // ApplicationManager.getApplication().getService(ReadWriteActionSupport::class.java);
        // a relaxed-mock application returns Object, which ClassCast-bombs and the JUnit5
        // TestUncaughtExceptionHandler fails the test even though our assertion passes.
        mockkObject(LocalReviewSettings.Companion)
        every { LocalReviewSettings.getInstance() } returns settings
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test fun class_implements_McpToolset() {
        assertTrue(
            LocalReviewToolset::class.isSubclassOf(McpToolset::class),
            "LocalReviewToolset must implement McpToolset so ReflectionToolsProvider picks it up.",
        )
    }

    @Test fun declares_exactly_the_five_expected_tool_names() {
        val names = toolAnnotatedFunctions(LocalReviewToolset::class).mapNotNull {
            it.findAnnotation<McpTool>()?.name
        }.toSet()

        assertEquals(
            setOf(
                "local_review_list_changes",
                "local_review_mark_all_viewed",
                "local_review_unmark_all",
                "local_review_mark_files",
                "local_review_unmark_files",
            ),
            names,
            "Tool name set is part of the user-prompt contract; changes must be deliberate.",
        )
    }

    @Test fun every_tool_has_a_non_empty_description() {
        val missing = toolAnnotatedFunctions(LocalReviewToolset::class).filter {
            it.findAnnotation<McpDescription>()?.description.isNullOrBlank()
        }
        assertTrue(missing.isEmpty(), "Missing @McpDescription on: ${missing.map { it.name }}")
    }

    @Test fun every_tool_is_a_suspend_function() {
        val nonSuspend = toolAnnotatedFunctions(LocalReviewToolset::class).filterNot { it.isSuspend }
        assertTrue(
            nonSuspend.isEmpty(),
            "Reflection-scanned MCP tools must be suspend fun. Non-suspend: ${nonSuspend.map { it.name }}",
        )
    }

    // ---------------------------------------------------------------------
    // Gate: every tool entry point refuses when the setting is off
    // ---------------------------------------------------------------------

    @Test fun list_changes_throws_when_disabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { toolset.local_review_list_changes() }
        }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun mark_all_viewed_throws_when_disabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { toolset.local_review_mark_all_viewed() }
        }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun unmark_all_throws_when_disabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { toolset.local_review_unmark_all() }
        }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun mark_files_throws_when_disabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { toolset.local_review_mark_files(listOf("/x")) }
        }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun unmark_files_throws_when_disabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { toolset.local_review_unmark_files(listOf("/x")) }
        }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun no_tool_permitted_helper_throws_with_disabled_message() {
        val ex = assertThrows(IllegalStateException::class.java) { noToolPermitted() }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("disabled", ignoreCase = true))
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun gateOff() {
        every { settings.current() } returns LocalReviewSettings.State(enableMcpTools = false)
    }

    private fun toolAnnotatedFunctions(kclass: KClass<*>): List<KFunction<*>> =
        kclass.declaredMemberFunctions.filter { it.findAnnotation<McpTool>() != null }
}
