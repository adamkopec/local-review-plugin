# Publishing Local Review to JetBrains Marketplace

This is the end-to-end checklist for each release. It combines the repo-side
Gradle steps with the Marketplace admin-panel steps (things that can't live in
code). Reference: the [JetBrains Marketplace best practices
guide](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html).

## One-time setup

Before the very first release:

- [ ] **Check plugin name availability.** Search
  <https://plugins.jetbrains.com/> for `Local Review` — confirm no existing
  plugin shadows the name. Marketplace name conflicts block the upload.
- [ ] **Create a Marketplace developer account** at
  <https://plugins.jetbrains.com/author/me>.
- [ ] **Generate a permanent upload token** at
  <https://plugins.jetbrains.com/author/me/tokens>. Save it as `PUBLISH_TOKEN`
  in your shell profile (or GitHub Actions secrets if you automate publishing).
- [ ] **Optional — plugin signing.** If you want JetBrains to display the
  signed-by-author badge, request a signing certificate from JetBrains and
  export it as `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
  Marketplace accepts unsigned plugins too — they're verified by checksum.
- [ ] **Capture screenshots and/or a demo GIF** — see [Screenshots](#screenshots) below.

## Per-release: repo side

1. Make sure all work for the release is merged into `main`.
2. Move the `[Unreleased]` section of `CHANGELOG.md` into a new versioned entry:

   ```
   ./gradlew patchChangelog
   ```

   (This task is provided by the `org.jetbrains.changelog` Gradle plugin and
   writes the release date automatically. Review the diff before committing.)
3. Bump `pluginVersion` in `gradle.properties`. **It must match the tag
   exactly** — the release workflow fails the build otherwise.
4. Commit + tag the release:

   ```
   git commit -am "Release 0.X.Y"
   git tag 0.X.Y             # no "v" prefix — matches the existing tag history
   git push --follow-tags
   ```

   Pushing the tag triggers `.github/workflows/release.yml`, which runs
   `check` → `verifyPlugin` → `buildPlugin` → `publishPlugin` on the tagged
   commit and then creates a GitHub Release with the zip attached and the
   CHANGELOG section for that version as the body.

   Non-stable versions (e.g. `0.3.1-beta.1`) match the second tag pattern in
   the workflow and go to a matching channel; stable versions go to the
   default channel. The channel routing is configured in `build.gradle.kts` →
   `intellijPlatform.publishing.channels`.

### Manual fallback

If the workflow is down or you need to publish from a branch that isn't
tagged (e.g. a one-off re-upload of the same version to a different channel),
run the same chain locally:

```
./gradlew check verifyPlugin buildPlugin
export PUBLISH_TOKEN=...
# optional, only if you configured signing:
# export CERTIFICATE_CHAIN=... PRIVATE_KEY=... PRIVATE_KEY_PASSWORD=...
./gradlew publishPlugin
```

## Per-release: Marketplace admin-panel side

After the first upload the plugin appears in
<https://plugins.jetbrains.com/author/me/plugins>. Open it and fill these fields
— none of them can be pushed from the repo; the admin panel owns them.

### On the **General** tab

- [ ] **Category** — pick the closest match. Recommended: **VCS Integration**
  (primary). Secondaries: **Code Tools**, **User Interface**.
- [ ] **Tags** — select ≥ 1. Recommended: *VCS*, *Code Review*, *Git*.
  If the right tag is missing email `marketplace@jetbrains.com`.
- [ ] **Display Name** — should already be `Local Review` (from `plugin.xml`).

### On the **Overview** tab

- [ ] **Description** — already injected from `README.md`'s
  `<!-- Plugin description -->` block via `patchPluginXml`. No action needed
  unless you want Marketplace-only copy different from the README.
- [ ] **Getting Started** — optional override. The description block already
  contains a `Getting started` paragraph; use this field only if you want a
  richer version with screenshots or an action-step list.
- [ ] **Screenshots** — upload the captures from [Screenshots](#screenshots)
  below (≥ 1200 × 760 px). Up to 10.
- [ ] **Demo video** — optional. YouTube link, ≤ 5 min, action-focused.

### On the **Additional Information** tab

- [ ] **Source Code URL** — `https://github.com/adam-kopec/local-review`
- [ ] **Documentation URL** — `https://github.com/adam-kopec/local-review#readme`
  (points at the full README). Don't use this field if the docs are already
  inline in the description; Marketplace hides the link in that case.
- [ ] **Issue Tracker URL** — `https://github.com/adam-kopec/local-review/issues`
- [ ] **Forum / Community URL** — leave blank unless you enable GitHub
  Discussions or a Slack.

### On the **Monetization** tab (optional)

- [ ] **Donations** — available for free plugins. Request access from JetBrains
  if you want the donate button on the listing. Donation prompts show to users
  who rate the plugin 4-5 stars.
- [ ] **Pricing** — skip unless you plan to offer a commercial license through
  Marketplace billing. The dual-licensing story in `README.md` works fine with
  out-of-band commercial licensing via email.

## Post-upload sanity check

Open the live Marketplace listing in both themes. Verify:

- [ ] First line of the description reads cleanly and the first 40 characters
  make sense on their own (search snippets).
- [ ] All inline links in the description resolve.
- [ ] The plugin icon is crisp at both 40 × 40 and ~16 × 16 (list view). Check
  light and dark themes.
- [ ] Compatibility banner shows the expected IDE range (`2024.1 – 2025.*`
  currently).
- [ ] Change notes for the current version are visible on the release page.

## What won't prevent a release

The following are called out in the best-practices guide but don't block us:

- **Embedded images in description** — we don't include any (the guide
  discourages this explicitly).
- **Marketing adjectives** — already avoided in `README.md`'s description block
  (no "simple", "lightweight", "powerful", etc.).
- **Non-English description** — we're English-only.
- **Brand references we don't own** — "GitHub" is used once in a purely
  descriptive context ("brings GitHub's 'Viewed' checkbox to…"). Acceptable.

## Screenshots

Marketplace requires at least one screenshot and shows them prominently.
**Minimum 1200 × 760 px**, PNG or GIF, legible text, IDE interface only (no
desktop chrome, no browser windows, no personal info).

Store captures in this directory: `src/main/resources/marketplace/` or any
folder you like — Marketplace doesn't pull them from the repo; you upload them
in the admin panel.

Capture in both light and dark themes where practical. Animated GIFs carry more
information per slot than PNGs — keep each under 5 MB, ≤ 10 seconds.

Shot list:

1. **Commit tool window, synthetic groups — hero shot.** Show the `Changes`
   section, `To review (N)`, and `Reviewed N of M · XX%` groups all visible,
   with at least one file showing the inline ✓ badge.
2. **Inline ✓ close-up.** Two or three rows zoomed in, one marked, the rest
   unmarked — demonstrates the inline affordance.
3. **Context menu — Mark as Reviewed.** Right-click menu on a changed file
   (editor tab is probably the most "I didn't know I could do that" surface).
4. **Auto-invalidation — animated GIF.** ~8 seconds: file marked reviewed →
   user types in the editor → ✓ disappears, file flips back to `To review`,
   counter updates.
5. **Settings page.** `Settings → Tools → Local Review` with TTL, per-branch
   cap, debug-log toggles.
6. **Status-bar widget** (optional). Close-up of `Reviewed 3/12` with hover
   tooltip.

Capture tips:

- Bump the IDE font to 14–16 pt before capturing — Marketplace compresses.
- Hide irrelevant tool windows (Project, Database, Services). Close extra tabs.
- Use a sample project with generic content; no real names, no API keys in
  filenames.
- Keep a consistent aspect ratio (16:9 or 16:10) across all shots so the
  carousel looks uniform.

## Troubleshooting

- **`publishPlugin` fails with 401** — `PUBLISH_TOKEN` is missing or expired.
  Re-issue at <https://plugins.jetbrains.com/author/me/tokens>.
- **`verifyPlugin` fails on a newer IDE** — either bump
  `pluginUntilBuild` in `gradle.properties` after verifying the code runs, or
  investigate the reported API incompatibility. Don't blindly widen the range.
- **Marketplace review rejects the submission** — email the
  `marketplace@jetbrains.com` feedback link in the rejection message. Most
  first-upload rejections are description / screenshot quality issues.