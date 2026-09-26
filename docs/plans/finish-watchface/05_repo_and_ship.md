# 05 — Repo & ship: remote, main, merge

Last phase. Current state: one local commit (`8a3e40c`) on
`fix/hc-fallback`, no remote, no `main`. Nothing here can run
until files 01–04 are done (don't back up a broken tree).

## R-1 — Remote + push (human-led)

**A. Human prereqs.** YOU create the empty GitHub repo (private
recommended — design assets + future keystore-adjacent files live
here), then:
`git remote add origin <url>`
`git push -u origin fix/hc-fallback`

**B. Pinned contracts.** `git remote` is currently empty (verified).
`.gitignore` already covers `*.jks` (file 03), `build/`, `.gradle/`,
`Adobe After Effects Auto-Save/`.

**C. Agent tasks.** Verify: `git ls-remote origin` succeeds;
`git status` clean; confirm no `.jks`, no `build/`, no Auto-Save
content in `git ls-files`.

**D. Acceptance command.**
`git ls-files | Select-String "\.jks$|/build/|Auto-Save"` → empty.

**E. Human leftovers.** Repo creation + push itself.

## R-2 — Main branch + merge + tag

**A. Human prereqs.** R-1 done, files 01–04 accepted.

**B. Pinned contracts.** HEAD = `fix/hc-fallback` @ `8a3e40c`
(+ plan commits). No divergence possible (single branch).

**C. Agent tasks.**
1. `git checkout -b main; git merge --ff-only fix/hc-fallback`
   (linear history — fast-forward must succeed; if it doesn't,
   STOP and report, do not force).
2. `git tag v1.0; git push -u origin main --tags`.
3. Optionally move `docs/plans/finish-watchface/` to
   `docs/_archive/` — only if that convention gets created;
   do not invent archive dirs unasked.

**D. Acceptance command.**
`git log --oneline -3 main` shows the release commit on top;
`git tag` lists `v1.0`; GitHub shows `main` + tag.

**E. Human leftovers.** Delete `fix/hc-fallback` if desired; keep
wearing the face.
