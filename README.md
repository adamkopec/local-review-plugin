# Local Review

An IntelliJ Platform plugin that adds GitHub-style "reviewed" checkmarks to the
Commit / Local Changes view, with automatic invalidation when a file changes after
being marked.

<!-- Plugin description -->
Local Review brings GitHub's "Viewed" checkbox to IntelliJ's Commit / Local Changes
view — so you can keep track of which files in your working tree you've already reviewed.

It's designed for reviewing AI-generated, auto-refactored, or machine-edited code
locally: mark a file reviewed, and if a tool touches it again the mark drops. On
IntelliJ 2025.2+ the agent can even drive the state via MCP — *"mark these files reviewed."*

**Features**

- Mark / unmark files as reviewed from the Commit tool window, an editor tab,
  the editor itself, or the Project View — or from an AI agent via MCP.
- `To review (N)` and `Reviewed N of M · XX%` synthetic groups in Local Changes
  give at-a-glance progress.
- Inline ✓ badge on reviewed rows.
- Auto-invalidation on any document edit, save, or external file change.
- State is per-project, per-branch, stored locally in `.idea/cache/` — never leaks
  into commits.
- Configurable TTL and per-branch cap keep state bounded.
- Works across the IntelliJ Platform: IDEA, PyCharm, WebStorm, GoLand, Android Studio, ...

**Source and issues:** [github.com/adamkopec/local-review-plugin](https://github.com/adamkopec/local-review-plugin)
<!-- Plugin description end -->

**Getting started**

Install the plugin, open any project with a Git working tree, then open the Commit
tool window. Right-click any changed or unversioned file and pick **Mark as Reviewed**,
or press `Ctrl+Alt+Shift+V` (`⌘⌥⇧V` on macOS). Edit the file later and the mark drops
automatically. Configure retention and cap under **Settings → Tools → Local Review**.

## Installation

Install from the JetBrains Marketplace:

- In the IDE, open **Settings → Plugins → Marketplace**, search for
  **Local Review**, and click **Install**.
- Or visit the listing directly:
  <https://plugins.jetbrains.com/plugin/31415-local-review>.

### Install from source

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
- Per-branch cap — evict the oldest marks when a branch exceeds it.
- Enable debug logging (restart required).
- Expose "viewed" tools to MCP-connected AI agents (see below). Grayed out when
  the MCP Server plugin isn't installed.

## MCP integration

When the JetBrains MCP Server plugin is installed (bundled in IDEs 2025.2+, on
the Marketplace for 2024.3–2025.1), Local Review exposes five tools that external
AI agents — Claude Desktop, Cursor, the JetBrains AI Assistant, `mcp-inspector`,
etc. — can invoke:

| Tool | Purpose |
|---|---|
| `local_review_list_changes` | Return JSON array of changed files with viewed status. |
| `local_review_mark_all_viewed` | Mark every file in the current changeset as viewed. |
| `local_review_unmark_all` | Clear all viewed marks for the project. |
| `local_review_mark_files` | Mark specific files (absolute or project-relative paths). |
| `local_review_unmark_files` | Unmark specific files. |

So a prompt like *"mark all files in this changeset as reviewed"* reaches the plugin
and flips the same state the UI toggles.

**Privacy.** When the integration is enabled, these tools expose the paths and
viewed-state of files in your current changeset to connected MCP clients. All data
stays on your machine — nothing is sent to Local Review's vendor. Disable the
integration under **Settings → Tools → Local Review** at any time.

**Silent activation.** The setting defaults to on, so if you install the MCP
Server plugin after Local Review, the tools become discoverable without further
configuration. Flip the checkbox off if you'd rather opt in explicitly.

### Connecting Claude Code

Installing the MCP Server plugin inside the IDE only runs the *server* side —
Claude Code still needs a client-side entry to reach it, even when you launch
Claude from the official IntelliJ Claude Code plugin. Anthropic's plugin ships
its own lightweight `ide` bridge (diagnostics only) and does not auto-wire
itself to the JetBrains MCP Server, so Local Review's tools won't appear until
you connect the two explicitly.

The one-click path — **recommended**:

1. In IntelliJ, open **Settings → Tools → MCP Server**.
2. Find **Claude Code** in the clients list and click **Configure**.
3. Approve the access prompt. The plugin adds an `sse` entry under
   `mcpServers.idea` in `~/.claude.json`, pointing at the IDE's local MCP
   endpoint (e.g. `http://127.0.0.1:<port>/sse`).
4. In your Claude Code session, run `/mcp` to reload (or start a new session).
   The `mcp__idea__local_review_*` tools should now appear alongside the rest
   of the JetBrains toolset.

If you prefer editing config by hand, open the same Settings panel to read the
active SSE URL, then add an equivalent `sse` entry to `~/.claude.json`
yourself. The URL's port changes per IDE restart, so the auto-config button is
the maintenance-free option.

## Development

```
./gradlew runIde           # launches a sandbox IDE with the plugin
./gradlew test             # runs the headless test suite (unit + integration)
./gradlew verifyPlugin     # runs JetBrains Plugin Verifier across target IDEs
./gradlew buildPlugin      # produces the distributable zip
```

### Tests

Two layers of regression coverage:

1. **Headless tests** (`./gradlew test`) — default, fast, deterministic.
   - *Unit tests* for `ContentHasher`, `Key`, `State` round-trip, `ReviewStateService`.
   - *Light-platform tests* for the service's PSC behavior via `BasePlatformTestCase`.
   - *Integration tests* in `src/test/kotlin/com/localreview/integration/` cover the
     end-to-end invalidation flows (mark → document edit → mark drops, mark → VFS save
     with different content → mark drops, same content → mark stays, etc.) using the
     real `EditorFactory`, `VirtualFileManager`, and `ChangeListManager`.

2. **Remote Robot UI smoke tests** (`./gradlew uiTest`) — slow, last-line safety net.
   Exercise a real sandbox IDE over HTTP via the JetBrains Remote Robot framework.
   Run in two terminals:

   ```
   # Terminal 1
   ./gradlew runIdeForUiTests

   # Terminal 2 — once the IDE window is up
   ./gradlew uiTest
   ```

   The current smoke suite checks that the action is registered, the plugin's
   application service is instantiable, and the bundle strings render correctly.
   Extend `src/uiTest/kotlin/.../LocalReviewUiSmokeTest.kt` for richer flows.

   `.github/workflows/run-ui-tests.yml` runs the same flow on Linux, Windows,
   and macOS — **manual trigger only** (`workflow_dispatch`), not on every push.
   On Linux it wraps the IDE in Xvfb; all three legs wait for the Robot port
   to become reachable, then run `./gradlew uiTest` and upload
   `build/reports/tests/uiTest/` as an artifact.

## Publishing

See [PUBLISHING.md](PUBLISHING.md) for the full release + Marketplace listing
checklist (pre-flight, Gradle commands, admin-panel fields to fill).

## License

`SPDX-License-Identifier: GPL-3.0-or-later`

Local Review is released under the [GNU General Public License v3.0 or later](LICENSE).
Forks and modified redistributions must be licensed under GPL-3.0 (or a later
compatible GPL version).

### Commercial licensing

The author (Adam Kopeć) is the sole copyright holder and may grant alternative
(non-GPL) licenses for use cases that are incompatible with GPL-3 — e.g. embedding
the plugin inside a closed-source product distribution. Contact
<adam.kopec@gmail.com> to discuss.

Contributions, if any, are accepted under GPL-3.0-or-later and must be the
contributor's own work (or accompanied by explicit permission from the rights
holder).
