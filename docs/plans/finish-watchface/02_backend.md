# 02 — Backend: data chain, trackers, permissions, rollover

Scope: everything the face reads. Files: `HealthStatsTracker.kt`
(tier 1), `SensorStatsTracker.kt` (tier 2), `SamsungHealthStats.kt`
×2 flavors (tier 3), `HealthConnectStatsTracker.kt` (workout stat),
`PermissionRequestActivity.kt` (grant flow).

Chain reminder (gloss once): the face tries sources in order and uses
the first one with data — Google Health Services, then the raw
SensorManager chip readings, then Samsung's proprietary SDK. A tier
being absent must show `--`, never crash.

## BE-1 — Verify tier 1 + tier 2 on the generic flavor

**A. Human prereqs.** Same device as FE-1 (emulator or non-Samsung
watch), permissions granted.

**B. Pinned contracts.**
- `HealthStatsTracker.start()` gates on BODY_SENSORS +
  ACTIVITY_RECOGNITION, then `SensorStatsTracker.init()` +
  `SamsungHealthStats.start()` (stub on generic).
- `heartRateText()` priority: Health Services → SensorManager →
  Samsung (`HealthStatsTracker.kt:173-178`).
- `stepsText()` falls back to `SensorStatsTracker.latestSteps()`
  (TYPE_STEP_COUNTER, cumulative-since-boot — NOT a daily total).

**C. Agent tasks.**
1. Logcat-assert passive callback fires
   (`onNewDataPointsReceived` path) or document its absence on the
   emulator with timestamps.
2. Confirm `Heartrate:` line shows a number within 5 min of wear, or
   `-- BPM` with a logged reason (no permission / no sensor).
3. Confirm no crash when Health Services is entirely absent
   (the stub + SensorManager path must carry the face alone).

**D. Acceptance command.**
`adb logcat -d | Select-String "HealthStatsTracker|SensorStatsTracker|FATAL"`
expect zero FATAL; HR line numeric or `--` with a logged cause.

**E. Human leftovers.** Wear the generic build 10 min; report which
tier actually fed HR.

## BE-2 — Validate the midnight rollover (overnight, unskippable)

**A. Human prereqs.** Samsung watch on wrist overnight with the
`samsungRelease` (or debug) face active. Nothing to install.

**B. Pinned contracts.** `HealthStatsTracker.kt:72-79` resets
steps/calories/distance/floors when `LocalDate.now()` changes; deltas
are summed per `STEPS_DAILY`/`CALORIES_DAILY`/`DISTANCE_DAILY`/
`FLOORS_DAILY` callbacks. Author notes this as best-effort.

**C. Agent tasks.** None (observation only). Next morning, human runs:
`adb logcat -d | Select-String "Steps:"` — n/a; instead read the face:
steps should be a small morning number, not yesterday's total.

**D. Acceptance command.** Morning screenshot shows steps < 2000
(or otherwise consistent with a fresh day), and no stuck/frozen stat
line (tap through all 8 stats).

**E. Human leftovers.** The whole task: wear it to bed, check in the
morning, report numbers. If rollover fails, file a follow-up (likely
fix: rebase on Health Services daily totals instead of local sums).

## BE-3 — Lock the tier-3 operating procedure (no code)

**A. Human prereqs.** None.

**B. Pinned contracts.** `SamsungHealthStats.kt:22-25,81-89`:
without partner registration the service returns SDK_POLICY_ERROR
unless Health Platform dev mode is on (Settings → Apps →
Health Platform → tap title ~10×). Already verified working.

**C. Agent tasks.** None. Decision recorded in `00_overview.md`:
personal-use-only → keep dev-mode procedure as the documented
setup step; add it to a `SETUP.md` note if one is created in file 05.

**D. Acceptance command.** n/a (procedure, not code).

**E. Human leftovers.** Re-enable dev mode after any Health Platform
app update (updates can reset it — if HR drops to `--`, this is the
first thing to check).

## BE-4 — End-to-end the Health Connect workout stat

**A. Human prereqs.** Health Connect installed (phone or watch),
one logged workout (real or Samsung Health sample).

**B. Pinned contracts.**
`HealthConnectStatsTracker.kt:38,54-60`: 5-minute poll,
`EXERCISE_DURATION_TOTAL` aggregated today; `workoutDurationText()`
returns `""` when SDK unavailable (stat silently drops from the
cycle — by design).

**C. Agent tasks.**
1. Grant via PermissionRequestActivity's Health Connect contract.
2. Wait ≤6 min, logcat-assert the aggregate query ran without
   exception; read the 8th stat on the face.

**D. Acceptance command.**
`adb logcat -d | Select-String "HealthConnect|FATAL"` — zero FATAL;
face shows `Workout: N min` (or `--` only if no workout exists).

**E. Human leftovers.** Log one real workout; confirm the number.
