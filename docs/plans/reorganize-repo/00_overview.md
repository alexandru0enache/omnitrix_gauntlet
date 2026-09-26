# Repository Reorganization Plan

## Current State

The repo root is a 16,648-file mess mixing code, assets, personal notes, and auto-generated files:

| Directory | Files | Problem |
|---|---|---|
| `OmnitrixWatchFace/` | 2,108 | Already well-organized Android project |
| `BG Complications/` | 3,600 | Raw image frames at root |
| `MAIN BG 1/` | 3,600 | Raw image frames at root |
| `season 1/` | 3,617 | Raw image frames at root |
| `Transition 2/` | 2,802 | Raw image frames at root |
| `.venv/` | 881 | Python venv at root |
| `docs/` | 17 | Obsidian vault (personal notes) |
| `Adobe After Effects Auto-Save/` | 10 | AE auto-saves at root |
| `omnitrix watchface_AME/` | 1 | AE export folder at root |
| Root-level files | ~8 | Loose `.png`, `.blend`, `.pdf` |
| `OmnitrixWatchFace/logcat_dump.txt` | 1 | 14,000+ line Android logcat — not gitignored |

---

## Target Structure

```
omnitrix_gauntlet/
├── README.md                          # NEW — project overview, screenshots, build instructions
├── .editorconfig                      # NEW — cross-editor formatting
├── .gitignore                         # UPDATED — see Phase 3 details
├── .gitattributes                     # NEW — Git LFS config for image frames
│
├── OmnitrixWatchFace/                 # UNCHANGED — Android project (already well-organized)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── local.properties               # machine-specific, already gitignored
│   ├── gradle/libs.versions.toml
│   ├── wear-app/
│   │   └── src/
│   │       └── libs/samsung-health-sensor-api-1.4.1.aar   # ← verify path still resolves
│   └── .gitignore                     # UPDATED — add logcat and other build artifacts
│
├── assets/                            # NEW — all design/media
│   ├── backgrounds/                   # BG Complications, MAIN BG 1, season 1
│   ├── transitions/                   # Transition 2
│   ├── design/                        # blender files + watchface_design (includes .ai)
│   │   ├── dna-strand.blend
│   │   ├── dna-strand.blend1
│   │   └── layout/design_01_v1.ai
│   ├── reference/                     # Samsung manual PDF
│   │   └── Watch-Face-Studio-Manual-Samsung.pdf
│   └── moodboard/                     # moodboard_folder + root PNGs (AOD_, Choose ET_)
│
├── docs/                              # Obsidian vault (personal notes) — stays nested
│   ├── plans/
│   │   ├── finish-watchface/
│   │   └── reorganize-repo/           # ← this file lives here
│   ├── moodboard_folder/
│   ├── demo_1.md                      # design note — stays in docs/
│   └── .trash/                        # Obsidian trash — gitignored
│
├── ae-autosave/                       # NEW — moved from root, gitignored
├── _export/                           # NEW — moved from root, gitignored
└── logs/                              # NEW — moved logcat_dump.txt here, gitignored
```

> **Note:** `.obsidian/` stays *inside* `docs/` (nested, not lifted to root). This is the standard Obsidian vault layout.

---

## Progress Status

| Phase | Description | Status |
|---|---|---|
| 1 — Create Directories | All target dirs created | ✅ **Complete** |
| 2a — Move Background Assets | BG Complications, MAIN BG 1, season 1 → assets/backgrounds/ | ✅ **Complete** |
| 2b — Move Transition Assets | Transition 2 → assets/transitions/ | ✅ **Complete** |
| 2c — Move Design Assets | watchface_design/, blend files → assets/design/ | ✅ **Complete** |
| 2d — Move Reference & Moodboard | PDF + PNGs → assets/reference/, assets/moodboard/ | ✅ **Complete** |
| 2e — Move AE Workspace Items | Adobe After Effects Auto-Save, omnitrix watchface.aep → ae-autosave/ | ✅ **Complete** |
| 2f — Move Export & Logs | _export/, logcat_dump.txt → _export/, logs/ | ✅ **Complete** |
| 3 — Update .gitignore | Add new patterns to root and module .gitignore | ✅ **Complete** |
| 4 — Add Scaffolding Files | README.md, .editorconfig, lint.xml, .gitattributes | ✅ **Complete** |
| 5 — Clean Up & Verify | Remove empty dirs, verify Gradle builds | ⏳ **Pending** |
| 6 — Git LFS Decision | Commit .gitattributes and track image frames | ⏳ **Pending** |

> **See [map.md](../../../map.md) for the full verification checklist and pending decision log.**

---

## Phase 1: Create Directories

```powershell
# Assets
New-Item -ItemType Directory -Force -Path 'assets/backgrounds'
New-Item -ItemType Directory -Force -Path 'assets/transitions'
New-Item -ItemType Directory -Force -Path 'assets/design'
New-Item -ItemType Directory -Force -Path 'assets/reference'
New-Item -ItemType Directory -Force -Path 'assets/moodboard'

# Workspace dirs
New-Item -ItemType Directory -Force -Path 'ae-autosave'
New-Item -ItemType Directory -Force -Path '_export'
New-Item -ItemType Directory -Force -Path 'logs'
```

---

## Phase 2: Move Files (with exact git commands)

> **Use `git mv`** to preserve history for all moved files. Directories with spaces must be quoted.

### ✅ Already Completed (Phases 2a–2d, 2f)

The following moves have already been executed:

| Item | From | To |
|---|---|---|
| `BG Complications/` | root | `assets/backgrounds/BG Complications/` |
| `MAIN BG 1/` | root | `assets/backgrounds/MAIN BG 1/` |
| `season 1/` | root | `assets/backgrounds/season 1/` |
| `Transition 2/` | root | `assets/transitions/Transition 2/` |
| `watchface_design/` | root | `assets/design/watchface_design/` |
| `dna strand.blend` | root | `assets/design/dna strand.blend` |
| `dna strand.blend1` | root | `assets/design/dna strand.blend1` |
| `Watch-Face-Studio-Manual-Samsung.pdf` | root | `assets/reference/Watch-Face-Studio-Manual-Samsung.pdf` |
| `AOD_00000.png`, `AOD_2_00000.png`, `Choose ET_00008.png` | root | `assets/moodboard/` |
| `OmnitrixWatchFace/logcat_dump.txt` | OmnitrixWatchFace/ | `logs/logcat_dump.txt` |

### ⏳ Pending Moves (Phase 2e — AE Workspace)

```powershell
# Move AE auto-save folder → ae-autosave/
git mv 'Adobe After Effects Auto-Save/' 'ae-autosave/'

# Move AE project file → ae-autosave/
git mv 'omnitrix watchface.aep' 'ae-autosave/'
```

### ✅ Decisions Completed

All pending items from map.md have been resolved:

| File | Action Taken |
|---|---|
| `OMNITRIX.wfs` | Moved to `OmnitrixWatchFace/` |
| `Screenshot 2023-12-17 220323.png` | Moved to `docs/` |
| `check*.png` (12 files) | Deleted from repo |
| `ID App names.txt` | Moved to `docs/` |

---

## Phase 3: Update `.gitignore`

### Root `.gitignore` — add these entries:

```
# AE / AME workspace
ae-autosave/
_export/

# Obsidian / notes
docs/.trash/
docs/.obsidian/
.obsidian/

# Blender auto-saves
*.blend1

# Build logs
*.logcat*
*.log
logs/

# Empty / unused dirs
watchface-style_classes/
```

### Module-level `OmnitrixWatchFace/.gitignore` — add these entries:

```
# Logcat / debug output
*.logcat*
*.log
logcat_dump.txt

# Additional Android artifacts
*.apk
*.apks
*.aab
*.dex
*.jks
*.keystore
```

---

## Phase 4: Add Missing Files

### 4a. `README.md` (repo root)

- Project name, description (Android Wear OS watch face)
- Screenshots (reference `OmnitrixWatchFace/wear-app/src/main/res/drawable-nodpi/watch_face_preview.png`)
- Build instructions:
  - Android SDK (compileSdk 36, minSdk 33)
  - Gradle wrapper (`./gradlew assembleDebug`)
  - Samsung variant (`./gradlew :wear-app:assembleSamsungDebug`)
  - Generic variant (`./gradlew :wear-app:assembleGenericDebug`)
- Source structure overview
- Asset organization note

### 4b. `.editorconfig` (repo root)

- Kotlin indentation (4 spaces)
- Max line width (120)
- Charset UTF-8
- Trailing whitespace removal

### 4c. `OmnitrixWatchFace/lint.xml` (module root)

- Baseline lint issues to suppress
- Configure checks relevant to watch face development

### 4d. `.gitattributes` (repo root)

- Git LFS config for large image sequences:
  ```
  assets/backgrounds/**/*.png filter=lfs diff=lfs merge=lfs -text
  assets/transitions/**/*.png filter=lfs diff=lfs merge=lfs -text
  ```

---

## Phase 5: Clean Up & Verify

### 5a. Remove empty / unnecessary dirs

```powershell
Remove-Item 'watchface-style_classes/' -Recurse -Force   # empty (0 files)
```

### 5b. Verify Gradle paths resolve

Run from `OmnitrixWatchFace/`:

```powershell
./gradlew :wear-app:assembleGenericDebug --dry-run
```

- Verify `libs/samsung-health-sensor-api-1.4.1.aar` path still resolves
- Verify no broken resource references (e.g., `dial_active.png` in `drawable-nodpi/`)

### 5c. Build verification (full)

```powershell
./gradlew :wear-app:assembleGenericDebug
./gradlew :wear-app:assembleSamsungDebug
```

### 5d. Verify source references

The Kotlin source files reference `dial_active.png` and `dial_ambient.png` in `drawable-nodpi/`:
- `OmnitrixWatchFace/wear-app/src/main/res/drawable-nodpi/dial_active.png` — **stays in place** (already correct)
- `OmnitrixWatchFace/wear-app/src/main/res/drawable-nodpi/dial_ambient.png` — **stays in place** (already correct)
- Comments in `DialPainter.kt`, `OmnitrixWatchFaceService.kt`, `FavoritesComplication.kt` reference `937x937` source art — **no changes needed** (these are documentation comments)

---

## Phase 6: Git LFS Decision (before committing)

The three background folders contain 3,600+ image frames each (~10,800+ total frames). **Before committing the reorganized repo**, decide:

| Option | Pros | Cons |
|---|---|---|
| **Git LFS** (recommended) | Tracks frames efficiently, small git history | Requires LFS installed, larger working directory |
| **Exclude from git** | Clean git history | Lose version history of assets |
| **Compress to archives** | Keeps history, smaller repo | Manual extraction needed, loses individual frame history |

**Recommendation:** Use Git LFS for `assets/backgrounds/**/*.png` and `assets/transitions/**/*.png`. Commit the `.gitattributes` config and run `git lfs track` on these patterns.

---

## Key Principles

- **`OmnitrixWatchFace/` stays untouched** — it's already well-organized
- **Assets get a home** — no more 16,000+ files polluting the root
- **Personal workspace stays separate** — Obsidian vault, AE auto-saves clearly distinguished from code
- **`README.md` fills the biggest gap** — no project overview existed
- **`.gitattributes` + Git LFS** — handles the massive image frame sequences properly
- **`local.properties`** — stays in `.gitignore`; anyone cloning will regenerate it automatically via Gradle

---

## Implementation Details (map.md → plan integration)

> This section implements the "map" concept: concrete, step-by-step actions to execute. **See [map.md](../../../map.md) for the complete verification map and decision log.**

### Execution Order

1. **Phase 1** — Create directories (5-10 seconds, no risk)
2. **Phase 2** — Move files with `git mv` (~30 seconds, atomic per file)
3. **Phase 3** — Update `.gitignore` and commit immediately after each phase
4. **Phase 4** — Add missing scaffolding (README, .editorconfig, lint.xml)
5. **Phase 5** — Verify Gradle paths resolve before finalizing
6. **Phase 6** — Decide Git LFS strategy, then commit with `.gitattributes`

### Risk Mitigation

- **Each phase is git-committable independently** → can rollback if needed
- **Use `--dry-run` for Phase 5b** before executing destructive moves
- **Keep original paths as refs**: `git update-ref -d "original-root" 2>/dev/null || true` (optional)

### Verification Checklist

> **Full checklist available in [map.md](../../../map.md#verification-checklist-post-execution)**

Before committing the reorganized state:

**From map.md (root-level verification):**
- [ ] All background/transition folders moved to `assets/`
- [ ] Design assets and reference PDF in correct locations
- [ ] AE workspace items moved to `ae-autosave/` or `_export/`
- [ ] No loose test artifacts at root (`check*.png`, etc.)
- [ ] All pending decisions from map.md resolved

**Build verification:**
- [ ] Gradle builds succeed for both generic and Samsung variants
- [ ] No broken resource references in Kotlin sources
- [ ] `.gitignore` patterns cover all build artifacts

**Git configuration:**
- [ ] Git LFS tracking configured for image frames
- [ ] `.gitattributes` committed alongside asset changes

### Rollback Plan (if needed)

If something goes wrong, restore from the commit *before* Phase 1:

```bash
git checkout <commit-before-phase1> -- .
git reset --hard <commit-before-phase1>
git clean -fdx assets/ ae-autosave/ _export/ logs/
```

### Estimated Timeline

| Phase | Duration | Risk Level |
|---|---|---|
| 1 — Create dirs | ~5s | Negligible (✅ done) |
| 2a–2d, 2f — Move assets | ~30s | Low (git mv is atomic) (✅ done) |
| 2e — Move AE items + decisions | ~1min | Low (✅ done) |
| 3 — Update .gitignore | ~5s | Negligible (✅ done) |
| 4 — Add scaffolding | ~1min | Negligible (✅ done) |
| 5b — Verify Gradle | ~60s (dry-run) | Medium |
| **Total remaining** | **~1 minute** | **Low** |

> **Status:** Phases 1–4 complete. Remaining: Phase 5 (Gradle verification) and Phase 6 (Git LFS commit).

---

## Final Commit Message Template

```bash
git commit -m "refactor: reorganize repo structure

- Move assets to assets/ subdirectory with proper categorization
- Move workspace directories (ae-autosave, _export) out of source tree
- Update .gitignore patterns for build artifacts and temporary files
- Configure Git LFS for large image frame sequences
- Add README.md with project overview and build instructions

See docs/plans/reorganize-repo/00_overview.md for detailed plan.
"
```