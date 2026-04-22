# Local Review — Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
  stabilises.
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
