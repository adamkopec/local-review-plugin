package pl.archiprogram.localreview.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    @Before
    fun setUp() {
        mockkObject(LocalReviewSettings.Companion)
        every { LocalReviewSettings.getInstance() } returns settings
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun classImplementsMcpToolset() {
        assertTrue(
            "LocalReviewToolset must implement McpToolset so ReflectionToolsProvider picks it up.",
            LocalReviewToolset::class.isSubclassOf(McpToolset::class),
        )
    }

    @Test fun declaresExactlyTheFiveExpectedToolNames() {
        val names =
            toolAnnotatedFunctions(LocalReviewToolset::class).mapNotNull {
                it.findAnnotation<McpTool>()?.name
            }.toSet()

        assertEquals(
            "Tool name set is part of the user-prompt contract; changes must be deliberate.",
            setOf(
                "local_review_list_changes",
                "local_review_mark_all_viewed",
                "local_review_unmark_all",
                "local_review_mark_files",
                "local_review_unmark_files",
            ),
            names,
        )
    }

    @Test fun everyToolHasANonEmptyDescription() {
        val missing =
            toolAnnotatedFunctions(LocalReviewToolset::class).filter {
                it.findAnnotation<McpDescription>()?.description.isNullOrBlank()
            }
        assertTrue("Missing @McpDescription on: ${missing.map { it.name }}", missing.isEmpty())
    }

    @Test fun everyToolIsASuspendFunction() {
        val nonSuspend = toolAnnotatedFunctions(LocalReviewToolset::class).filterNot { it.isSuspend }
        assertTrue(
            "Reflection-scanned MCP tools must be suspend fun. Non-suspend: ${nonSuspend.map { it.name }}",
            nonSuspend.isEmpty(),
        )
    }

    // ---------------------------------------------------------------------
    // Gate: every tool entry point refuses when the setting is off
    // ---------------------------------------------------------------------

    @Test fun listChangesThrowsWhenDisabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { toolset.local_review_list_changes() }
            }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun markAllViewedThrowsWhenDisabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { toolset.local_review_mark_all_viewed() }
            }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun unmarkAllThrowsWhenDisabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { toolset.local_review_unmark_all() }
            }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun markFilesThrowsWhenDisabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { toolset.local_review_mark_files(listOf("/x")) }
            }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun unmarkFilesThrowsWhenDisabled() {
        gateOff()
        val toolset = LocalReviewToolset()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { toolset.local_review_unmark_files(listOf("/x")) }
            }
        assertTrue(ex.message.orEmpty().contains("disabled", ignoreCase = true))
    }

    @Test fun noToolPermittedHelperThrowsWithDisabledMessage() {
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
