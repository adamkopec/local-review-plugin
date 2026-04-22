package pl.archiprogram.localreview.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Structural checks on the two plugin manifest files. These fail fast when someone:
 *  - accidentally references the [pl.archiprogram.localreview.mcp] package from the main
 *    `plugin.xml` (which would break classloading on IDEs without MCP Server);
 *  - typos the MCP extension namespace (which fails silently — tools simply don't register);
 *  - forgets to register one of the five tools.
 */
class PluginManifestTest {

    @Test fun main_plugin_xml_does_not_reference_mcp_package() {
        val xml = readResource("META-INF/plugin.xml")
        assertFalse(
            xml.contains("pl.archiprogram.localreview.mcp"),
            "main plugin.xml must not reference the mcp package — classes there must only be " +
                "reachable via the optional pl.archiprogram.localreview-mcp.xml descriptor.",
        )
    }

    @Test fun mcp_descriptor_has_correct_namespace() {
        val xml = readResource("META-INF/pl.archiprogram.localreview-mcp.xml")
        assertTrue(
            xml.contains("""defaultExtensionNs="com.intellij.mcpServer""""),
            "MCP descriptor must declare extensions under the com.intellij.mcpServer namespace; " +
                "a typo there silently drops the tool registrations.",
        )
    }

    @Test fun mcp_descriptor_registers_the_toolset_with_its_fully_qualified_class() {
        val xml = readResource("META-INF/pl.archiprogram.localreview-mcp.xml")
        val pattern = Regex(
            """<mcpToolset\s+implementation="(pl\.archiprogram\.localreview\.mcp\.[A-Za-z]+)"\s*/>""",
        )
        val impls = pattern.findAll(xml).map { it.groupValues[1] }.toSet()

        assertEquals(
            setOf("pl.archiprogram.localreview.mcp.LocalReviewToolset"),
            impls,
            "MCP descriptor must register exactly LocalReviewToolset under <mcpToolset>.",
        )
    }

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Missing classpath resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
