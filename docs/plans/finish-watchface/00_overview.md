# Finish the Omnitrix watch face — overview

## Goal (zero ambiguity)
A shippable face: `generic` and `samsung` release APKs install clean,
show live stats, survive a full day on wrist, and the repo is backed up
to a remote. "Finished" = signed release on the Galaxy Watch +
verified generic fallback + `main` branch pushed.

## What already works (do not regress)
- 3-tier HR chain on Samsung hardware: Health Services → SensorManager →
  Samsung PrivSDK 1.4.01.00 streaming real HR/IBI (`HeartRateSensorData`,
  94 bpm observed). Commit `8a3e40c`, branch `fix/hc-fallback`, tree clean.
- 6 fixed complication slots + 6 stacked favorite slots (ids 20–25) with
  tap-to-cycle arrows and system-picker escape hatch.
- Ambient/AOD face + ambient-exit pop-in animation, 8-stat cycling line,
  external-exercise override line.

## Phase order (read in this order)
1. `01_frontend.md` — face UI: renderer, complications, taps, activities.
2. `02_backend.md` — data chain: all four trackers, permissions, rollover.
3. `03_build_and_release.md` — versioning, signing, manifest hardening.
4. `04_validation.md` — device matrix, overnight, battery. Runs after 1–3.
5. `05_repo_and_ship.md` — remote, `main`, merge. Last.

## Cross-cutting decisions (made here, not per-file)
- **Personal-use-only.** No Samsung partner registration, no Play
  release. Tier 3 keeps requiring Health Platform dev mode on the watch;
  the chain already degrades gracefully without it.
- **No unit tests.** Single-screen canvas app; ROI is in on-device
  logcat asserts (defined in `04_validation.md`), not JVM tests.
- **Minify stays off.** Obfuscation buys nothing for a sideloaded face
  and risks breaking the local `.aar` linkage.

## Human-only steps (nothing below can do these)
- Create the GitHub repo + `git remote add` (file 05).
- Create the release keystore (file 03).
- The overnight/battery wear sessions (file 04).
- Answer: personal-use-only — confirm or correct (changes file 03 scope).
