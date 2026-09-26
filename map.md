# Project Mapping — Reorganization Verification Map

This document maps every root-level file and directory to its final destination. Use this as a **checklist** before committing to verify the reorganization is complete and correct.

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | Already moved/completed |
| ⏳ | Pending — needs action in Phase 2 or later |
| ❌ | Should be deleted/ignored (not committed) |
| ➡️ | Will be created as a new directory |
| 📝 | Needs decision on destination |

---

## Root → Target Mapping Table

### Directories

| Current Location | Files | Target Location | Status | Notes |
|---|---|---|---|---|
| `BG Complications/` | 3,600 | `assets/backgrounds/BG Complications/` | ✅ Done | Git LFS tracked PNG frames |
| `MAIN BG 1/` | 3,600 | `assets/backgrounds/MAIN BG 1/` | ✅ Done | Git LFS tracked PNG frames |
| `season 1/` | 3,617 | `assets/backgrounds/season 1/` | ✅ Done | Git LFS tracked PNG frames |
| `Transition 2/` | 2,802 | `assets/transitions/Transition 2/` | ✅ Done | Git LFS tracked PNG frames |
| `watchface_design/` | ~15 | `assets/design/watchface_design/` | ✅ Done | Includes `.ai` layout files |
| `dna strand.blend` | 1 | `assets/design/dna strand.blend` | ✅ Done | Blender source |
| `dna strand.blend1` | 1 | `assets/design/dna strand.blend1` | ✅ Done | Blender auto-save, gitignored |
| `Watch-Face-Studio-Manual-Samsung.pdf` | 1 | `assets/reference/Watch-Face-Studio-Manual-Samsung.pdf` | ✅ Done | Reference manual |
| `AOD_00000.png` | 1 | `assets/moodboard/AOD_00000.png` | ✅ Done | Moodboard reference |
| `AOD_2_00000.png` | 1 | `assets/moodboard/AOD_2_00000.png` | ✅ Done | Moodboard reference |
| `Choose ET_00008.png` | 1 | `assets/moodboard/Choose ET_00008.png` | ✅ Done | Moodboard reference |
| `Adobe After Effects Auto-Save/` | 10 | `ae-autosave/Adobe After Effects Auto-Save/` | ✅ Done | Gitignored after move |
| `omnitrix watchface_AME/` | 1 | `_export/omnitrix watchface_AME/` | ✅ Done | Contains AE export project |
| `OmnitrixWatchFace/logcat_dump.txt` | 1 | `logs/logcat_dump.txt` | ✅ Done | Moved, will be gitignored |

### Root-Level Files

| Current Location | Type | Target Location | Status | Notes |
|---|---|---|---|---|
| `.venv/` | Python venv | **Stay at root** (gitignore) | ❌ Ignore | Add to `.gitignore`, do NOT move |
| `watchface-style_classes/` | Empty dir | **Deleted** | ✅ Done | Removed in Phase 5a |
| `omnitrix watchface.aep` | AE project | `ae-autosave/omnitrix watchface.aep` | ✅ Done | AE workspace, gitignored after move |
| `OMNITRIX.wfs` | Watch face source | `OmnitrixWatchFace/OMNITRIX.wfs` | ✅ Done | Moved to project dir |
| `Screenshot 2023-12-17 220323.png` | Screenshot | `docs/Screenshot 2023-12-17 220323.png` | ✅ Done | Moved to docs/ |
| `check*.png` (12 files) | Test screenshots | **Deleted** | ✅ Done | Removed from repo |
| `ID App names.txt` | Text file | `docs/ID App names.txt` | ✅ Done | Moved to docs/ |

### Files Already at Root (No Change Needed)

| Location | Status | Notes |
|---|---|---|
| `.gitignore` | ✅ Updated | Added new patterns in Phase 3 |
| `README.md` | ✅ Updated | Project overview with build instructions |
| `.editorconfig` | ✅ Created | Cross-editor formatting config |
| `.gitattributes` | ✅ Created | Git LFS config for image frames |

---

## Verification Checklist (Post-Execution)

After running all phases, verify:

### ✅ Directories Created
- [x] `assets/backgrounds/` exists with 3 subfolders
- [x] `assets/transitions/` exists with Transition 2
- [x] `assets/design/` exists with watchface_design + blend files
- [x] `assets/reference/` exists with Samsung PDF
- [x] `assets/moodboard/` exists with 3 PNGs
- [x] `ae-autosave/` exists (contains AE items)
- [x] `_export/` exists
- [x] `logs/` exists

### ✅ Files Moved Correctly
- [x] No raw asset folders at root (`BG Complications`, `MAIN BG 1`, etc. gone)
- [x] No loose PNGs at root (except project README assets if any)
- [x] `ae-autosave/` contains AE auto-save and `.aep` file
- [x] `_export/` contains AME export folder
- [x] `logs/logcat_dump.txt` exists

### ✅ Files Gitignored
- [x] `*.blend1` patterns in root `.gitignore`
- [x] `ae-autosave/` in root `.gitignore`
- [x] `_export/` in root `.gitignore`
- [x] `docs/.trash/` and `docs/.obsidian/` in root `.gitignore`
- [x] `*.logcat*`, `*.log`, `logs/` in both root and module `.gitignore`
- [x] `.venv/` in root `.gitignore`

### ✅ New Files Created
- [x] `README.md` at root with project overview
- [x] `.editorconfig` at root
- [x] `OmnitrixWatchFace/lint.xml` (baseline lint suppressions)
- [x] `.gitattributes` at root with LFS config

### ⏳ Build Verification (Pending)
- [ ] Gradle dry-run succeeds from `OmnitrixWatchFace/`
- [ ] Samsung variant builds successfully
- [ ] Generic variant builds successfully
- [ ] No broken resource references in Kotlin sources

---

## Git History Preservation Notes

All moves use `git mv` to preserve file history:

```bash
# Example: this preserves the rename history for future git blame
git mv 'BG Complications/' 'assets/backgrounds/BG Complications/'
```

When viewing history after reorganization:
- `git log assets/backgrounds/BG Complications/001.png` will show commits from when the file was at root
- No `git filter-repo` or history rewrite needed — this is a clean, reversible refactor
