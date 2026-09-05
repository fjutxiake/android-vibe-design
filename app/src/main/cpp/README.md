# vibe_git — libgit2-backed version storage (#36)

JNI bridge over vendored libgit2 providing version snapshots & rollback.

- `vibe_git.c` — JNI layer: open-or-init (external git-dir), `add -A` commit, log,
  checkout-to-directory (atomic restore via pending swap), dirty check.
- `CMakeLists.txt` — builds libgit2 statically with all network/SSH features off; only
  `arm64-v8a` and `x86_64` are enabled in `app/build.gradle.kts` for now.
- `libgit2/` is **not committed**. The `fetchLibgit2` Gradle task (wired to `preBuild`)
  downloads and extracts v1.9.7 automatically on first build. Machines that need a
  proxy for GitHub should configure `systemProp.https.proxyHost/Port` in the user-level
  `~/.gradle/gradle.properties`; GitHub Actions runs fine without.

API surface lives in `com.aeibi.design.data.versions.git` (`Libgit2` + `Libgit2Repository`);
restoration policy lives in `com.aeibi.design.data.versions` (`GitVersionStorage` +
`VersionSnapshotService`).
