// Compile-time stubs for com.intellij.mcpserver.annotations.*. See package note in
// ../McpToolset.kt for why these stubs exist.
package com.intellij.mcpserver.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpTool(val name: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpDescription(val description: String)
