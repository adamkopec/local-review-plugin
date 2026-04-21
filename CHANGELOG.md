# Local Review — Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
