# 01 — Frontend: face UI, complications, taps, activities

Scope: everything the eye touches. Files:
`OmnitrixWatchFaceService.kt`, `OmnitrixCanvasRenderer.kt`,
`DialPainter.kt`, `FavoritesComplication.kt`, `FavoritesConfigActivity.kt`,
`DebugPreviewActivity.kt`, `PermissionRequestActivity.kt`,
`res/drawable-nodpi/*`, `res/xml/watch_face_info.xml`.

## FE-1 — Prove the generic flavor renders on real hardware

**A. Human prereqs.** Wear emulator booted (API 33+, round) OR any
non-Samsung Wear OS watch on adb. Android SDK already at
`C:\Users\enach\AppData\Local\Android\Sdk`.

**B. Pinned contracts.**
- Build: `OmnitrixWatchFace/wear-app/build.gradle.kts` flavors
  `generic`/`samsung`, dimension `device`.
- Entry: `OmnitrixWatchFaceService.createWatchFace()` →
  `OmnitrixCanvasRenderer`; tap path via `FavoritesTapListener`.
- Stub: `src/generic/.../SamsungHealthStats.kt` (no-op object, same
  surface as `src/samsung/.../SamsungHealthStats.kt`).

**C. Agent tasks (in order).**
1. `./gradlew :wear-app:installGenericDebug` from
   `E:\tech_root\personal_projects\omnitrix_gauntlet\OmnitrixWatchFace`.
2. Launch `PermissionRequestActivity` via adb, grant all.
3. Set the face, screenshot it, tap left/right arrows, screenshot each
   of the 5 favorites + custom slot picker.
4. Logcat-assert: no `SamsungHealthStats` connect lines, no
   `NoClassDefFoundError`, `SensorStatsTracker: hrSensor=true`.

**D. Acceptance command.**
`adb logcat -d | Select-String "SensorStatsTracker|FATAL|NoClassDefFoundError"`
expect: hrSensor line present, zero FATAL, zero NoClassDefFoundError.

**E. Human leftovers.** Eyeball the 6 screenshots (layout, legibility);
confirm the Samsung-weather default degrades to the system picker
instead of a dead slot.

## FE-2 — Kill the stat-list duplication before it drifts

**A. Human prereqs.** None.

**B. Pinned contracts.** Two parallel 8-stat lists exist TODAY and must
stay identical: `OmnitrixCanvasRenderer.kt:45-54` (`STAT_LIST`) and
`DebugPreviewActivity.kt:54-63` (`statList`). Same for the 5000 ms
cadence (`STAT_DISPLAY_MILLIS` vs literal `5000L` in
`PreviewView.onDraw`, line 69).

**C. Agent tasks.**
1. Extract one shared `StatLineProvider.list(): List<() -> String>`
   and one `STAT_CADENCE_MILLIS` const in `main/.../watchface/`;
   both call sites use them.
2. `./gradlew :wear-app:assembleGenericDebug :wear-app:assembleSamsungDebug`.

**D. Acceptance command.** `grep -rn "5000L\|STAT_DISPLAY_MILLIS" wear-app/src`
shows exactly one definition site; both flavors assemble with
zero warnings-as-errors.

**E. Human leftovers.** None.

## FE-3 — Harden the dev-only activities for release

**A. Human prereqs.** None (executes with file 03's manifest pass).

**B. Pinned contracts.** `AndroidManifest.xml:38-40` —
`DebugPreviewActivity exported=true`. `PermissionRequestActivity`
is the legitimate launcher entry ("Omnitrix Permissions").

**C. Agent tasks.**
1. `DebugPreviewActivity` → `exported=false` (dev preview only).
2. Keep `PermissionRequestActivity` + `FavoritesConfigActivity`
   exported (user-facing flows).
3. Rebuild both flavors.

**D. Acceptance command.**
`aapt dump xmltree app-*.apk AndroidManifest.xml | Select-String "exported"`
shows `false` only on DebugPreviewActivity.

**E. Human leftovers.** None.
