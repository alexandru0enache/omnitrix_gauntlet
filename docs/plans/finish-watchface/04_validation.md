# 04 — Validation: device matrix, overnight, battery

Runs AFTER files 01–03. Every check is a logcat assert or a
screenshot — no instrumentation, no unit tests (decision in
00_overview.md).

## V-1 — Device matrix (the full pass/fail table)

**A. Human prereqs.** Galaxy Watch (samsungRelease), emulator or
second watch (genericRelease), both permission-granted.

| # | Device / flavor | Check | Pass signal |
|---|---|---|---|
| 1 | Samsung / samsung | HR live | `HeartRateSensorData` in logcat, numeric BPM on face |
| 2 | Samsung / samsung | AOD + wake pop | ambient art dim, overlay pops on wake, no flicker |
| 3 | Samsung / samsung | Favorites cycle | all 5 favorites + custom picker open |
| 4 | Emulator / generic | Boots, no crash | zero FATAL, stats show numbers or `--` with cause |
| 5 | Emulator / generic | No Samsung linkage | zero `NoClassDefFoundError`, zero `samsung` log tags |
| 6 | Either | Perms denied | face runs, all stats `--`, zero crashes |

**B. Pinned contracts.** Tags: `SamsungHealthStats`,
`SensorStatsTracker`, `FavoritesTapListener`, `FATAL`.

**C. Agent tasks.** Execute rows 1–6, one adb session each, save
screenshots + `logcat -d` excerpts per row.

**D. Acceptance command (per row).**
`adb logcat -d | Select-String "FATAL|NoClassDefFoundError"` → empty;
row-specific signal present.

**E. Human leftovers.** Eyeball AOD legibility in a dark room (row 2);
confirm tap targets feel right (row 3).

## V-2 — Battery day (unskippable, human-led)

**A. Human prereqs.** Full charge, normal day, samsungRelease face.

**B. Pinned contracts.** Always-on listeners: HR (UI cadence),
step counter (NORMAL), Samsung continuous tracker, 5-min Health
Connect poll, 5-s stat repaint (interactive only), 60-s frame
cadence. None measured yet.

**C. Agent tasks.** None. Evening: human reads Settings → Battery
on the watch, notes % and screen-on time vs. the previous face.

**D. Acceptance command.** Judgment call: if the day ends within
~10% of the old face's drain, PASS. If materially worse, the
follow-up is duty-cycling tier 2/3 listeners (new plan, not this one).

**E. Human leftovers.** The whole task. Report the number.
