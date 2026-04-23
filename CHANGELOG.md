# Local Review — Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0]

Full alignment with the JetBrains `intellij-platform-plugin-template` stack. Minimum supported
IDE is now **IntelliJ 2025.2.6.1** — older 2024.x versions are no longer supported.

### Changed

- **Toolchain bumped across the board**: Gradle 8.13 → 9.4.1, IntelliJ Platform Gradle Plugin
  2.9.0 → 2.14.0 (wired via `org.jetbrains.intellij.platform.settings` in `settings.gradle.kts`),
  Kotlin 2.0.21 → 2.1.20, Kover 0.8.3 → 0.9.8, Changelog 2.2.1 → 2.5.0, mockk 1.13.12 → 1.14.9,
  JDK 17 → 21.
- **IDE floor raised to 2025.2.6.1.** Dropped `pluginSinceBuild` / `pluginUntilBuild` / platform
  version parameterization from `gradle.properties`; the plugin now targets exactly the IDE
  version declared via `intellijIdea("2025.2.6.1")` in `build.gradle.kts`.
- **MCP Server is now a required dependency** (`<depends>com.intellij.mcpServer</depends>`).
  Merged the former optional descriptor into `plugin.xml`; deleted the compile-time stubs
  (`com.intellij.mcpserver.*`) and the `isMcpServerPluginAvailable()` presence helper — users on
  2025.2+ always have MCP. The settings dialog unconditionally enables the MCP toggle.
- **JUnit 4 replaces JUnit 5** throughout the test suite (matching the template). 18 test files
  converted; `@Nested` classes flattened to method-name-prefixed tests.
- **CI pipeline rewritten** to mirror the template: `ci.yml` replaced with sequential
  `build.yml` (`build → test → verify`, parallel `coverage`) + a separate
  `workflow_dispatch`-triggered `run-ui-tests.yml` matrixed across Ubuntu / Windows / macOS.
  Every job opens with `jlumbroso/free-disk-space@v1.3.1`. Action versions now:
  `actions/checkout@v6`, `actions/setup-java@v5` (Zulu / JDK 21),
  `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`.
- `LocalReviewToolset` migrated from the removed `ProjectContextElement` API to the current
  `com.intellij.mcpserver.project` extension on the coroutine context.
- `GitBranchListener` now takes an injectable `refresh: (Project) -> Unit` so tests drive the
  refresh count through a lambda instead of `mockkObject(SafeRefresh)`.
- Extracted a narrow `ReviewState` interface from `ReviewStateService` so MCP-logic unit tests
  can exercise the code through a plain-Kotlin `FakeReviewState` — mockk's inline instrumentation
  no longer collides with the IDE's coroutines-debug javaagent on 2025.2+.

### Fixed

- Remote Robot UI smoke test: added `--add-opens java.base/java.lang=ALL-UNNAMED` to the
  `uiTest` JVM args so Gson can deserialize `RetrieveResponse.detailMessage` on JDK 17+.

## [0.2.1]

### Fixed

- Status-bar counter widget was missing on projects opened from a cold start.
  `StatusBarWidgetsManager` populates widgets before VCS mappings settle and only
  re-evaluates `isAvailable()` when a widget factory is added/removed — so the
  previous `hasActiveVcss()` gate lost the startup race and the widget never
  appeared for the session, even after marking files. `isAvailable()` is now
  unconditional (empty state is already handled by `getText()`), with a
  belt-and-braces `StatusBarWidgetsManager.updateWidget(...)` call from the
  post-startup activity.
- Plugin description and `What's New` changelog rendered `&quot;` as literal
  `quot;` in the JetBrains plugin manager because the HTML renderer dropped the
  leading `&`. Source markdown now uses Unicode curly quotes so there's no HTML
  entity to decode.

### Changed

- Bumped the IntelliJ Platform Gradle Plugin from 2.2.1 to 2.9.0. Fixes a sandbox
  IDE startup crash (`ClassNotFoundException: kotlinx.coroutines.debug.AgentPremain`
  → SIGABRT) that was blocking the Remote Robot UI smoke-test job in CI.
- GitHub Actions workflow bumped to `actions/checkout@v5`, `actions/setup-java@v5`,
  `actions/upload-artifact@v5`, `gradle/actions/setup-gradle@v5` — ahead of
  GitHub's June 2026 Node.js 20 cutover.
- Dropped an unused `mockkStatic(ApplicationManager::class)` / relaxed-mock
  `Application` field from `LocalReviewMcpLogicTest` and `LocalReviewToolsetTest`.
  It was intercepting `getService(ReadWriteActionSupport::class.java)` calls from
  a platform background coroutine (`ShadeIndexDumbModeTracker`) on the 2024.2 SDK
  and failing the test with an uncaught `ClassCastException`.

## [0.2.0]

### Added

- MCP Server integration. Five tools — `local_review_list_changes`,
  `local_review_mark_all_viewed`, `local_review_unmark_all`, `local_review_mark_files`,
  `local_review_unmark_files` — let external AI agents (Claude Desktop, Cursor, the
  JetBrains AI Assistant, etc.) drive the “viewed” state when the bundled MCP Server
  plugin is present (IntelliJ-based IDEs 2025.2+). Enabled by default; disable under
  **Settings → Tools → Local Review → Expose “viewed” tools to MCP-connected AI
  agents**. Tools only act on files in the current local changeset, matching the UI's
  “Mark as Reviewed” semantics. Implemented via the reflection-scanned
  `com.intellij.mcpserver.McpToolset` API — no dependency on the deprecated external
  `mcp-server-plugin`. All data stays on your machine.
- GPL-3.0-or-later license (`LICENSE` + SPDX identifier in README / plugin.xml).
- Integration test suite (`InvalidationFlowsIT`) covering document-edit invalidation,
  VFS-save invalidation with different content, VFS-save with identical content, and
  listener tolerance for files without a stored mark.
- Remote Robot UI smoke-test scaffolding (`src/uiTest/kotlin/`, `runIdeForUiTests` +
  `uiTest` Gradle tasks) — asserts action registration, service instantiation, and
  bundle strings against a live sandbox IDE.
- GitHub Actions `ui-tests` job: installs Xvfb, launches the sandbox IDE in the
  background, waits for the Remote Robot port, runs `uiTest`, and uploads the IDE
  log + screenshots on failure. Marked `continue-on-error: true` until the suite
  stabilizes.
- Marketplace listing readiness: extended the plugin description with a
  *Getting started* paragraph and inline source / issues link;
  added `PUBLISHING.md` with pre-flight + admin-panel release checklist
  and the screenshot capture spec.
- Real coverage gate: 77.7% line coverage on the logic layer, 70% threshold
  enforced on the CI `coverage` job (no longer `continue-on-error`).
  New tests: `KeyDeriverTest`, `DefaultBranchProviderTest`, `GitBranchListenerTest`
  (all MockK-based), `TargetCollectorTest`, `ChangeSetScannerTest`,
  `ToggleViewedActionTest`. Extracted `TargetCollector` and `ChangeSetScanner`
  to make action / listener logic unit-testable without a live IDE.

## [0.1.0]

### Added

- Mark / unmark files as reviewed from the Commit tool window, editor tab, editor
  right-click, and Project View right-click.
- Keyboard shortcut `Ctrl+Alt+Shift+V` (`⌘⌥⇧V` on macOS).
- `To review (N)` and `Reviewed N of M · XX%` synthetic groups in Local Changes.
- Inline ✓ badge on reviewed rows.
- Auto-invalidation: immediate on in-editor document edits; defensive rehash on
  VFS save and on every ChangeListManager update.
- Status-bar widget showing `Reviewed N/M`.
- Settings page under **Tools → Local Review** (TTL, per-branch cap, debug logging).
- Per-project, per-branch, local-only state stored in `.idea/cache/` (never
  `workspace.xml`, so review marks can't leak into commits).
- Rename detection via `ChangeListManager` — marks follow the new path.
- Optional Git4Idea integration for branch-scoped state; graceful fallback to
  a `<no-branch>` sentinel when Git isn't available (SVN, standalone projects).
