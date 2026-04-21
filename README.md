# Local Review

An IntelliJ Platform plugin that adds GitHub-style "reviewed" checkmarks to the
Commit / Local Changes view, with automatic invalidation when a file changes after
being marked.

<!-- Plugin description -->
**Local Review** brings GitHub's "Viewed" checkbox to IntelliJ's Commit / Local Changes
view — so you can keep track of which files in your working tree you've already reviewed.

It's designed for reviewing AI-generated, auto-refactored, or machine-edited code locally:
mark a file as reviewed, and if a code-gen tool, formatter, or your own editor touches
it again, the mark drops automatically so you don't miss the new churn.

**Features**

- Mark / unmark files as reviewed from the Commit tool window, an editor tab,
  the editor itself, or the Project View.
- `To review (N)` and `Reviewed N of M · XX%` synthetic groups in Local Changes
  give at-a-glance progress.
- Inline ✓ badge on reviewed rows.
- Auto-invalidation on any document edit, save, or external file change.
- State is per-project, per-branch, stored locally in `.idea/cache/` — never leaks
  into commits.
- Configurable TTL and per-branch cap keep state bounded.
- Works across the IntelliJ Platform: IDEA, PyCharm, WebStorm, GoLand, Android Studio, ...
<!-- Plugin description end -->

## Installation

_(Pending first release to JetBrains Marketplace.)_

Until then, build locally:

```
./gradlew buildPlugin
```

The plugin zip lands in `build/distributions/`. Install it via
**Settings → Plugins → ⚙ → Install Plugin from Disk…**

## Keyboard shortcut

`Ctrl+Alt+Shift+V` (macOS: `⌘⌥⇧V`) toggles the reviewed mark on the current selection.

## Settings

Under **Settings → Tools → Local Review**:

- TTL (days) — forget reviewed marks older than this.
- Per-branch cap — evict oldest marks when a branch exceeds it.
- Enable debug logging (restart required).

## Development

```
./gradlew runIde           # launches a sandbox IDE with the plugin
./gradlew test             # runs the test suite
./gradlew verifyPlugin     # runs JetBrains Plugin Verifier across target IDEs
./gradlew buildPlugin      # produces the distributable zip
```

## Publishing

Set environment variables:

- `PUBLISH_TOKEN` — JetBrains Marketplace upload token
  ([get one here](https://plugins.jetbrains.com/author/me/tokens))
- _(Optional, if you're signing the plugin)_ `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
  `PRIVATE_KEY_PASSWORD` — JetBrains plugin signing credentials

Then:

```
./gradlew patchChangelog   # shifts [Unreleased] → [x.y.z] in CHANGELOG.md
./gradlew publishPlugin    # signs (if configured) and uploads to Marketplace
```

## License

_(TODO — add a license file.)_
