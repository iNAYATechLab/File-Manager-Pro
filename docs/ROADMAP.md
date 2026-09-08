# File Manager Pro — Development Roadmap

> Versioning: Semantic Versioning (SemVer) via `release-please`. While the app is
> in development (0.x), every release is marked **prerelease**. `feat` bumps MINOR
> (0.1.3 → 0.2.0), `fix` bumps PATCH, breaking changes bump MAJOR.
> Workflow: every item lands on `main` through a `feature/*`/`fix/*` branch →
> PR → **green CI** → owner merge → automatic prerelease.

---

## M1 — Foundation & UX polish · target 0.2.x → 0.3.x

**Goal: a pleasant, trustworthy everyday file manager with storage visibility.**

- [x] CI/CD pipeline (build, lint, unit tests, signed prerelease APK)
- [x] Kotlin compile-error + startup-crash fixes, defensive view-binding
- [ ] **Storage home screen** — cards for every volume (internal + SD card) with
      used/free/total, tap to open, up-navigation back to home
- [ ] Localize hard-coded operation strings to `values` / `values-bn`
      (paste/delete/compress/extract/move messages)
- [ ] Settings screen (sort prefs, view type, hidden-files toggle, about)
- [ ] Hidden files toggle (currently dot-files are always hidden)
- [ ] First-run permission UX: friendly explainer before the system dialog
- [ ] Search: filters (All / Files / Folders) + optional whole-storage scope
- [ ] Espresso smoke test: app launch + storage listing + tab switch

## M2 — Power operations · target 0.4.x → 0.5.x

**Goal: no-compromise file operations.**

- [ ] Copy/move progress dialog with live counter, cancel, conflict handling
      (overwrite / skip / keep both)
- [ ] Extract support: `.tar`, `.tar.gz`, `.7z` (read), `.rar` (read)
- [ ] Folder metrics: recursive size/count preview before operations
- [ ] Duplicate finder & "large files" cleaner views
- [ ] Thumbnails in list view; video frame thumbnails
- [ ] Image viewer: pinch-zoom, EXIF panel, rotate, set-as-wallpaper
- [ ] Properties: show children count/size summary, path copy action

## M3 — Smart & secure · target 0.6.x → 0.8.x

**Goal: proactive, secure, delightful.**

- [ ] Recents / Downloads / APK quick-access tabs
- [ ] Storage analyzer (per-folder usage chart)
- [ ] SAF (Storage Access Framework) browsing for protected dirs (Android/data…)
- [ ] Encrypted vault: AES-encrypt folders to `.fmpvault`, unlock with PIN/biometric
- [ ] In-app text/code viewer (large files, UTF-8 + legacy encodings)
- [ ] Favorite folders + home shortcuts
- [ ] File tags / color labels

## M4 — 1.0.0 release hardening · target 1.0.0

**Goal: production release.**

- [ ] R8/ProGuard release minification with rule audit (Coil, coroutines)
- [ ] Full Espresso UI suite + `connectedCheck` in CI (emulator job)
- [ ] App icon variants (monochrome themed icons on 13+), splash screen
- [ ] Play Store listing assets: screenshots (EN/BN), feature graphic, privacy
      policy page
- [ ] Keystore finalization & roll-out plan (beta → staged → production)
- [ ] Tag `1.0.0` as a **stable** (non-prerelease) release

---

## Definition of Done (every item)

1. Feature implemented on a dedicated `feature/<name>` (or `fix/<name>`) branch
2. Architecture/quality maintained — no bypassed abstractions
3. Tests added/updated where meaningful; lint & build pass
4. Local validation done, diff reviewed, no secrets committed
5. Push → **CI green** → PR approved/merged by owner
6. Version bumped automatically → prerelease artifact attached & signed

## Status legend

- [x] shipped to `main`
- [ ] backlog item (issue link in GitHub Milestones M1–M4)
