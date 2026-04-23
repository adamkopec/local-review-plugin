package pl.archiprogram.localreview.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural checks on the plugin manifest. These fail fast when someone:
 *  - typos the MCP extension namespace (which fails silently — tools simply don't register);
 *  - forgets to register the toolset with its fully-qualified class name.
 */
class PluginManifestTest {
    @Test fun pluginXmlDeclaresMcpNamespace() {
        val xml = readResource("META-INF/plugin.xml")
        assertTrue(
            "plugin.xml must declare extensions under com.intellij.mcpServer; " +
                "a typo there silently drops the tool registrations.",
            xml.contains("""defaultExtensionNs="com.intellij.mcpServer""""),
        )
    }

    @Test fun pluginXmlRegistersTheToolsetWithItsFullyQualifiedClass() {
        val xml = readResource("META-INF/plugin.xml")
        val pattern =
            Regex(
                """<mcpToolset\s+implementation="(pl\.archiprogram\.localreview\.mcp\.[A-Za-z]+)"\s*/>""",
            )
        val impls = pattern.findAll(xml).map { it.groupValues[1] }.toSet()

        assertEquals(
            "plugin.xml must register exactly LocalReviewToolset under <mcpToolset>.",
            setOf("pl.archiprogram.localreview.mcp.LocalReviewToolset"),
            impls,
        )
    }

    private fun readResource(path: String): String {
        val stream =
            javaClass.classLoader.getResourceAsStream(path)
                ?: error("Missing classpath resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
