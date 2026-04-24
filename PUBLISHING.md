# Publishing Local Review to JetBrains Marketplace

This is the end-to-end checklist for each release. It combines the repo-side
Gradle steps with the Marketplace admin-panel steps (things that can't live in
code). Reference: the [JetBrains Marketplace best practices
guide](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html).

The plugin is published at
<https://plugins.jetbrains.com/plugin/31415-local-review>. Manage the listing
under <https://plugins.jetbrains.com/author/me/plugins>.

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

If the workflow is down, or you need to publish from a branch that isn't
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

Open the listing at
<https://plugins.jetbrains.com/author/me/plugins> (slug:
[`31415-local-review`](https://plugins.jetbrains.com/plugin/31415-local-review)).
The fields below live in the admin panel — not in the repo — and only need
revisiting when you change category, tags, screenshots, or description:
