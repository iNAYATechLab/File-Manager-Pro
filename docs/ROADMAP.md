# File Manager Pro — Development Roadmap

> **Canonical scope reference:** [docs/MASTER-FEATURE-LIST.md](MASTER-FEATURE-LIST.md) (PART F: *single master feature inventory*).
> This roadmap replaces the earlier draft roadmap and is derived 1:1 from the master list.
>
> **Status baseline:** checked against **v0.2.0** (signed prerelease). PART A (core/P0): 123 ✅ implemented · 18 🔶 partial · 221 ⬜ backlog.
> **Lifecycle:** every phase ships through `feature/*`/`fix/*` → PR → **green CI** → owner merge → automatic **prerelease** (SemVer: `feat`=MINOR, `fix`=PATCH, breaking=MAJOR). `docs:`/`ci:`/`test:`/`chore:` commits never trigger a release.

---

## Milestone map

| Phase | Focus | Master-list scope | Target version |
|---|---|---|---|
| ~~0~~ | ~~Platform & release engineering, core browser/ops baseline~~ | ~~§29–33, §2/5/6/8/9 core bits~~ | ~~v0.1.0–v0.2.0~~ ✅ shipped |
| 1 | Discovery & personalization (home, favorites, recent, settings) | PART A §1, §13, §14, §15, §27 | v0.3.x |
| 2 | Selection, search & display power | PART A §2, §4, §5 | v0.4.x |
| 3 | Operations, conflicts & archives | PART A §3, §8, §22 | v0.5.x |
| 4 | Media, documents & APK intelligence | PART A §6, §7, §16 | v0.6.x |
| 5 | Storage intelligence, safety & privacy core | PART A §9–§12, §17, §28 | v0.7.x |
| 6 | Core hardening → **1.0.0 stable** | all PART A done + R8, UI tests, Play assets | 1.0.0 |
| 7 | **P1 Essential Professional** | PART B §1–§10, §14, §16, §27, §33, §46 | 1.1.x |
| 8 | **P2 Advanced Professional** | PART B §15, §19–§26, §28–§32, §34, §35, §38–§41 | 1.2.x |
| 9 | **P3 Expansion / Enterprise** | PART B §17/18 deep, §29, §36, §37, §42–§45 | 2.x |

> PART A numbering = section numbers in the master list. PART B uses `PART B §n`.

---

# Phase 0 — Platform & core baseline (COMPLETE)

**Shipped (v0.1.0 → v0.2.0):** CI/CD pipeline (build, lint, unit tests, secret scan, branch protection, signed prerelease APK per merge), SemVer + release-please automation, keystore-managed signing, storage home with volume cards (internal + SD, used/free/total), core file browser (browse/open/create folder/rename/copy/move/delete/multi-select/share/properties), list/grid view, 4 sort keys, image category browser + full-screen swipe viewer, ZIP create/extract, basic search, EN + partial বাংলা UI.

Everything still `[ ]` in PART A below is the backlog this roadmap schedules.

---

# Phase 1 — Discovery & personalization · target v0.3.x

**Scope:** PART A §1 Home & Dashboard, §13 Favorites, §14 Recent Files, §15 Hidden Files, §27 Settings.

- Modern Home Dashboard (quick-access grid, category shortcuts, smart shortcuts)
- Quick Access + Recent Files + Recent Locations + Favorites (persistent, sortable, grouped)
- Downloads / Large Files / Trash shortcuts; Storage Health summary card
- Hidden files: show/hide toggle + indicator + settings-based default
- Settings screen: Appearance (theme, view mode, grid size, thumbnails, animations) + File Browser (default sort, folders-first, show hidden, default location)
- USB/OTG: first-class card (current detection is mount-based only)

**Exit:** every §1/§13/§14/§15/§27(partial) checkbox in PART A marked `[x]`; no crash on rotate/restore.

---

# Phase 2 — Selection, search & display power · target v0.4.x

**Scope:** PART A §2 (part), §4 Powerful Search (core half), §5 Sorting & Display.

- Create new file; Open-with (explicit chooser); Select-by-type / Select-by-extension
- Search: by extension / type / size / modified date / creation date; full-storage scope; advanced filters; search history + clear; search hidden files
- Display: Compact List, Large Grid, Thumbnail View polish, sort by Created date & Extension, Files-first ordering

**Exit:** search covers filters + whole storage; new selection & display options verified by tests.

---

# Phase 3 — Operations, conflicts & archives · target v0.5.x

**Scope:** PART A §3 Advanced File Operations, §8 Archive Manager, §22 File Operation Queue, §2 (cancel/retry).

- Operation progress dialog → live counter, cancel; operation queue (running/completed/failed), retry failed
- Background file operations (service), pause/resume where the platform allows, operation history
- Conflict resolution UI: detect conflicts, Replace / Skip / Keep both / Auto-rename / Apply to all
- Batch rename (sequential numbering, prefix/suffix, find-replace — basic engine first)
- Batch extract; batch type operations
- ZIP: browse contents, add/remove entries, archive details; password-protected ZIP (safe subset)
- Future formats (extract-first): TAR, GZIP, 7z; capability detection & graceful error

**Exit:** queue + conflict engine usable from multi-select; archive section §8 fully `[x]` for supported formats.

---

# Phase 4 — Media, documents & APK intelligence · target v0.6.x

**Scope:** PART A §6 Media, §7 Document Management, §16 APK/App files.

- Images: metadata (dimensions, EXIF where available), full-screen viewer polish
- Video: thumbnails, in-app preview or external handoff, metadata (codec/resolution where readable)
- Audio: metadata + in-app preview/playback; album/artist where available
- Documents: in-app preview for text / markdown / CSV / JSON / XML / HTML / source code (read-only, streaming for large files); PDF preview (first page/quick view); Office files → external open
- APK: file details, version/label where readable without privileged APIs; safe install handoff

**Exit:** §6/§7/§16 core checkboxes `[x]`; unknown formats fall back to Android “Open With” (per master list rule).

---

# Phase 5 — Storage intelligence, safety & privacy core · target v0.7.x

**Scope:** PART A §9 Storage Management, §10 Large Files, §11 Duplicates, §12 Trash, §17 Security & Privacy (core), §28 Diagnostics & Reliability.

- Storage: SAF browsing for protected dirs (Android/data, obb), category breakdown (usage analysis)
- Large Files analyzer: sort by size, top-N lists, size filters, batch select → delete/move, impact estimate
- Duplicate finder: filename + size prefilter, hash-based exact match, grouping, safe delete workflow
- Trash / Recycle Bin: move-to-trash, view, restore, permanent delete, empty, auto-clean policy, size — respecting Android storage-provider semantics
- Security core: device-auth app lock, recent-history controls, cache clearing, privacy controls
- Diagnostics: error reporting architecture, crash-safe ops, structured logging, storage/permission/version diagnostics (privacy-safe)

**Exit:** §9–§12 & §17(core)/§28 checkboxes `[x]`; destructive flows confirmed + recovery tested.

---

# Phase 6 — Core hardening → **1.0.0 stable**

**Scope:** PART A remainder (§18 Private Folder if still open), platform quality layer.

- Private Folder (auth-gated, protected browsing, secure metadata handling)
- R8/ProGuard minification audit (Coil, coroutines rules)
- Espresso UI suite (launch, browse, tabs, rotation) + `connectedCheck` in CI (emulator job)
- App icon variants + themed icons, splash screen; Play listing assets (EN/BN), privacy policy page
- Release rollout plan (beta → staged → production); **tag `1.0.0` as stable (non-prerelease)**

**Exit:** every PART A checkbox `[x]`; full quality gate green on main; 1.0.0 stable released.

---

# Phase 7 — P1 Essential Professional · target 1.1.x

**Scope:** PART B §1–§14 (media/document inspection incl. image/video/audio utilities §11–§13), §16, §27, §33, §46 + PART A §18 leftovers.

- §1 Advanced navigation & productivity: back/forward history, bookmarks/aliases, tabs (basic), quick switcher
- §2 Operation engine: persistent queue, prioritization, per-file & aggregate progress, ETA, checkpointing, retries, history DB, post-operation verification
- §3 Conflict engine advanced: compare size/time/hash, keep-newest/oldest/largest/smallest, apply-to-all, conflict preview
- §4 Batch rename engine: templates, counters, regex, case/extension transforms, date tokens, rename preview + collision simulation, presets
- §5 Search & indexing: boolean/regex, metadata search, saved searches, result sorting/dedup/export, background index + health/rebuild, exclusions
- §6 Storage intelligence: folder-size analysis, category drill-down, per-volume analysis, cleanup recommendations with impact simulation
- §7 Duplicate detection advanced: multi-stage, similarity groups, auto-selection rules, exclusions
- §8 File comparison: size/timestamp/hash, directory compare, missing/changed/new detection, export
- §9 Archives advanced: browse-without-extract, entry search & selective extraction, integrity test, add/remove/rename entries, 7z/TAR/GZIP/BZIP2/XZ
- §10–§13 Media intelligence: EXIF, dimensions, codec/bitrate/frame info, GPS where available; image/video/audio inspection utilities (rotation & metadata-preserving tools, external editor handoff)
- §14 Document tools: syntax view, line numbers, in-file search, JSON/XML/CSV structured viewers, encoding detection
- §16 Integrity: SHA-256/512 (+legacy), checksums, manifest generate/verify, batch verification, hash export

**Exit:** P1 professional core usable; every P1-relevant checkbox `[x]`.

---

# Phase 8 — P2 Advanced Professional · target 1.2.x

**Scope:** PART B §15, §19–§26, §28–§32, §34–§35, §38–§41.

- §15 Built-in text editor (syntax highlight, undo/redo, find/replace, encodings, crash-safe drafts)
- §19–§22 Network & cloud: SMB/FTP/SFTP/WebDAV, connection manager (profiles, test, safe credentials), Google Drive/OneDrive/Dropbox/Box, cross-provider transfers, transfer queue
- §23–§24 Advanced sharing & URI intelligence: share packaging/history/diagnostics, URI permission tools, provider routing
- §25 APK intelligence: manifest/package inspection, icon extraction, hashes, safe install handoff
- §26 Permission/capability inspector (read/write/delete/rename matrix)
- §28 Organization rules; §29 Smart automation (scheduled scans/reports); §30–§31 Reporting & inventory (CSV/JSON export)
- §32 Thumbnail & preview cache engine (adaptive sizes, eviction, stats/rebuild)
- §34–§35 Power-user & large-screen: tabs, dual/split pane, drag-and-drop, keyboard/mouse, folder tree + details pane, multi-window
- §38–§41 Diagnostics/recovery/security monitoring advanced (provider/SAF/network/cloud diagnostics, audit log, auto-lock, session mgmt)

**Exit:** P2 features usable; security monitoring enabled; no unnecessary permissions added.

---

# Phase 9 — P3 Expansion / Enterprise · target 2.x

**Scope:** PART B §17/18 (deep), §29 (advanced), §36, §37, §42–§45.

- §17/§18 Encryption & private vault: AES vaults, key derivation & secure storage, rotation, tamper detection, auto-lock/session expiry, recovery workflow, encrypted thumbnails
- §29 Advanced automation: scheduling with battery/network awareness, automation logs & failure reporting
- §36 Advanced accessibility; §37 per-location/preferences pro; §42 Full localization (RTL, pluralization, locale-aware sort/date)
- §43 Data persistence: metadata DB, migrations, state restoration
- §44 Testing infrastructure: property/stress/large-directory/network-failure/corruption suites
- §45 Compatibility layer: capability matrix, graceful degradation, provider fallbacks

**Exit:** enterprise-grade checklist `[x]`; full master list 100%.

---

# Platform quality (continuous, every phase)

- §29 Automated quality system: build · lint · unit tests · (new) UI tests · secret scanning · dependency vulnerability checks · static analysis — **all on every push/PR**
- §30 Versioning & release: conventional commits + SemVer + automatic prerelease artifacts (continues through 1.0.0 → stable)
- §31 CI/CD & audit trail per release
- §32 Documentation kept current: README, architecture notes, dev guide, changelog per release
- §33 Localization: complete বাংলা UI during Phase 1; architecture ready for more languages

---

# Working agreement (Definition of Done)

1. Work starts from a `feature/<name>` (or `fix/<name>`) branch cut from latest `main`.
2. The matching master-list checkbox is marked `[x]`/`[~]` in the **same PR** (single source of truth stays current).
3. Architecture rules from master list PART E hold (no UI-thread I/O, confirm destructive ops, no plaintext secrets, graceful degradation).
4. Tests/lint/build pass locally; **CI green** before merge; owner merges; release automation takes over.
5. This roadmap is updated whenever scope changes — never in parallel to the master list.

## Progress tracker

| Phase | Status |
|---|---|
| 0 — Core baseline | ✅ v0.1.0–v0.2.0 |
| 1 — Discovery & personalization | ⬜ |
| 2 — Selection, search & display | ⬜ |
| 3 — Operations, conflicts & archives | ⬜ |
| 4 — Media, documents & APK | ⬜ |
| 5 — Storage intelligence, safety & privacy | ⬜ |
| 6 — Core hardening → 1.0.0 stable | ⬜ |
| 7 — P1 Essential Professional | ⬜ |
| 8 — P2 Advanced Professional | ⬜ |
| 9 — P3 Expansion / Enterprise | ⬜ |
