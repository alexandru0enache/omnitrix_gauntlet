# 03 — Build & release: versioning, signing, manifest

Scope: `wear-app/build.gradle.kts`, `AndroidManifest.xml`,
keystore (new file, NEVER committed), release APKs.

## BR-1 — Version the app like a release

**A. Human prereqs.** Confirm personal-use-only (see 00_overview.md).

**B. Pinned contracts.** `build.gradle.kts:9-15`: `versionCode = 1`,
`versionName = "0.1-spike"`, `minSdk = 33`, `targetSdk = 34`.

**C. Agent tasks.**
1. `versionCode = 2`, `versionName = "1.0"`.
2. Keep `isMinifyEnabled = false` (decision in 00_overview.md).
3. Keep `allowBackup="true"` (harmless for a face; changing it
   wipes existing complication prefs on update — worse).

**D. Acceptance command.**
`./gradlew :wear-app:assembleSamsungRelease` then
`aapt dump badging app-samsung-release.apk | Select-String "versionCode|versionName"`
shows `2` / `1.0`.

**E. Human leftovers.** None.

## BR-2 — Signing (human-led, agent-verified)

**A. Human prereqs.** YOU run once, in PowerShell, inside
`OmnitrixWatchFace/` (a keystore is a password-protected identity
file; its password must never enter chat, tickets, or git):
`keytool -genkeypair -keystore omnitrix-release.jks -alias omnitrix -keyalg RSA -keysize 2048 -validity 10000`
Back up the `.jks` + password somewhere off-disk (password manager).

**B. Pinned contracts.** Nothing in repo yet — no `signingConfigs`
block exists. `.gitignore` must gain `*.jks` before the keystore
exists in the directory.

**C. Agent tasks.**
1. Add `*.jks` to `OmnitrixWatchFace/.gitignore`.
2. Add `signingConfigs { release { storeFile ..., storePassword
   System.getenv(...), ... } }` reading credentials from
   environment variables only (never hardcoded), wired into the
   `release` build type.
3. `./gradlew :wear-app:assembleSamsungRelease :wear-app:assembleGenericRelease`.

**D. Acceptance command.**
`apksigner verify --print-certs app-samsung-release.apk`
shows your cert (not the debug key); `git status --short` shows no
`.jks` file.

**E. Human leftovers.** Keystore backup; keep the env-var names
written down for future shells.
