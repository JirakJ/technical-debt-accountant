# Changelog

## [Unreleased]

### Fixed
- Settings page no longer shows options that had no effect (Inventory Path, Auto-detect debt items, Show gutter icons); the inventory always lives in `.tech-debt/inventory.json`.
- Invalid hourly rate / budget input in Settings falls back to safe defaults instead of being stored as-is.
- **Log Workaround Time** rejects zero, negative or non-numeric minutes instead of silently logging 15 minutes.
- CLI (`SlaCli`) reports an unreadable policy file as an error instead of crashing.

### Removed
- Unused Git integration service and the optional Git4Idea dependency, an unregistered gutter-icon renderer, an unused burndown calculator and the never-wired workaround timer API. Documentation no longer advertises these.

## [2026.8.0] - 2026-11-10

### Changed
- The plugin is now completely free — all features unlocked, no license needed.
- Source code is now open source under Apache-2.0: https://github.com/JirakJ/technical-debt-accountant

## [2026.7.0] - 2026-06-11

### Added
- **Debt Health Score** — a 0–100 score with an **A–F letter grade** that
  summarises the project's debt pressure at a glance. The score uses exponential
  decay over weighted priority severity, so it is bounded (0–100), can't be gamed
  negative, and stays comparable across projects. Surfaced in the Tech Debt tool
  window summary, in management reports (Executive Summary), and in a new status
  bar widget.
- **Debt Health status bar widget** — shows `Debt <grade> · <monthly interest>/mo`
  with a detailed tooltip (score, active items, monthly interest, budget status);
  click it to open the Tech Debt tool window. Refreshes automatically as the
  inventory changes.
- **Monthly debt budget** — new setting (`Settings → Tools → Technical Debt
  Accountant`); when set, the tool window and status bar flag the project as
  **over budget** once recurring monthly interest exceeds the threshold.
- 18 new unit tests covering grade thresholds/boundaries, score monotonicity and
  clamping, priority weighting, budget flag logic, and status bar text/tooltip
  formatting. Suite total: 238 tests, all green.

### Fixed
- **Thread-safety** — `DebtInventoryStore` now guards all reads and writes of the
  inventory with a lock, eliminating a `ConcurrentModificationException` risk when
  background tasks (scan import, SLA escalation) mutate the inventory while the EDT
  (tool window, status bar) reads it. Listener callbacks run outside the lock.

### Changed
- Refreshed the plugin icon (light + dark) to a cleaner, higher-contrast ledger mark.
- Build now provisions the JDK 17 toolchain via the Foojay resolver, so the project
  builds on machines without a pre-installed JDK 17.

## [2026.6.0] - 2026-06-09

### Added
- **TODO/FIXME Comment Scanner** — new `Tools → Scan TODO/FIXME Comments…`
  action walks the project tree, finds `TODO` / `FIXME` / `HACK` / `XXX` /
  `OPTIMIZE` comment markers (line, block, hash, HTML, and SQL comment styles),
  previews them in a table, and imports them as debt items with marker-based
  category, priority, and fix estimate. Imported items are tagged `scanned`
  and deduplicated on re-scan by file+line and file+description.
- `DebtScanCli` — CI-friendly CLI entry point with `--root`, `--inventory`
  (dedup against existing inventory), `--format=md|json`, `--out`, and
  `--fail-over=<n>` (exit 2 when more than `n` new markers are found).
- **CSV/JSON Inventory Export** — new `Tools → Export Debt Inventory…` action
  exports the backlog as RFC 4180 CSV (Excel/Sheets-friendly) or JSON with a
  summary block, including computed workaround and fix dollar costs from the
  configured hourly rate. Optionally includes resolved items.
- Marketplace-ready screenshots in `screenshots/` (tool window, scanner,
  add-debt dialog, management report, forecast, export), referenced from the
  README and listing docs.
- 55 new unit tests (JUnit 5) covering marker detection across comment styles,
  non-matches (lowercase, string literals, longer words), directory-walk skip
  rules and binary detection, dedup logic, CLI argument/exit-code paths, CSV
  escaping/quoting, ISO-8601 timestamps, and JSON summary math. Suite total:
  221 tests, all green.

### Fixed
- `AddDebtDialog` now validates input before saving: non-empty description,
  numeric non-negative line range with `lineEnd ≥ lineStart`, and a positive
  fix-time estimate.
- Tech Debt tool window updates triggered from background tasks (scan import,
  SLA escalation) are now dispatched on the EDT instead of the calling thread.
- `DebtInventoryStore` listener registration/notification is now thread-safe
  (`CopyOnWriteArrayList`).

## [2026.4.2] - 2026-04-17

### Changed
- Enabled freemium licensing via `<product-descriptor optional="true">`. Plugin remains fully functional without a license; premium features may be introduced in future minor releases.


## [2026.4.1] - 2026-04-17

### Changed
- Raised minimum compatibility to IntelliJ 2025.1 (build 251). Dropped
  support for 2024.3 (build 243) so the plugin can use the modern, non-
  deprecated platform APIs introduced in 2025.1.

### Fixed
- Replaced deprecated `FileSaverDescriptor(String, String, String...)` varargs
  constructor with the non-deprecated `(String, String, String)` constructor
  (single-extension filter) in `EvaluateSlaAction`, `GenerateForecastAction`,
  `GenerateWeeklyDigestAction`, and `SuggestSprintItemsAction`.
- Replaced deprecated / scheduled-for-removal
  `TextBrowseFolderListener(FileChooserDescriptor, Project?)` constructor in
  `DebtAccountantConfigurable` with the modern
  `TextFieldWithBrowseButton.addBrowseFolderListener(Project?, FileChooserDescriptor)`
  API.
- Replaced deprecated `LocalFileSystem.refreshAndFindFileByIoFile(File)` with
  `refreshAndFindFileByNioFile(Path)` in `GenerateForecastAction`.
- Updated plugin verifier matrix to target `2025.1.7`, `2025.2.6.1`,
  `2025.3.4`, and `2026.1`, matching JetBrains Marketplace verification.

## [2026.3.10] - 2026-04-17

### Fixed
- Replaced deprecated `FileSaverDescriptor(String, String, String...)` varargs
  constructor with the new `(String, String)` + `withExtensionFilter(...)`
  fluent builder in `EvaluateSlaAction`, `GenerateForecastAction`,
  `GenerateWeeklyDigestAction`, and `SuggestSprintItemsAction`.
- Replaced deprecated `TextFieldWithBrowseButton.addBrowseFolderListener(title,
  description, project, descriptor)` overload with the new
  `descriptor.withTitle(...).withDescription(...)` + `addBrowseFolderListener(
  project, descriptor)` API.
- Updated plugin verifier matrix to target `2024.3`, `2025.1.7`, `2025.2.6.1`,
  `2025.3.4`, and `2026.1` (EAP), matching JetBrains Marketplace verification.

## [2026.3.9] - 2026-04-17

### Added
- **Debt Trend Forecasting & Burndown Projection** — new `Tools → Debt Trend
  Forecast…` action and `DebtForecastCli` entry point that read persisted
  digest snapshots and project the backlog forward in time.
- `com.jakubjirak.debtaccountant.forecast.LinearRegression` — pure-Kotlin OLS
  implementation returning slope, intercept, r², and residual standard error.
- `com.jakubjirak.debtaccountant.forecast.ExponentialRegression` — log-linear
  fit for `y = a · exp(b · x)`.
- `ForecastModel` enum (`LINEAR` / `EXPONENTIAL` / `AUTO`) — `AUTO` picks the
  model with the higher r².
- `TimeSeriesPoint` + `SnapshotHistoryLoader` that reuse the existing
  `DigestSnapshotStore`, exclude resolved items, order chronologically, and
  deduplicate same-UTC-date snapshots (keep latest).
- `DebtForecaster` computes current running averages (7-day / 30-day), trend
  direction (`IMPROVING` / `FLAT` / `DEGRADING`) from the slope, per-priority
  trends, week-over-week count delta, burndown date estimate, and per-week
  predictions with a 95% confidence interval.
- `ForecastMarkdownReporter` with Executive Summary, Trend Analysis, Priority
  Breakdown, ASCII-sparkline Burndown, N-week Forecast Table, and Methodology
  sections.
- `ForecastHtmlReporter` — self-contained HTML with inline CSS and an inline
  SVG line chart showing predicted counts with a shaded 95% confidence band.
  No external fonts, scripts, or CDN references.
- `DebtForecastCli` supports `--snapshots-dir`, `--horizon-weeks` (default 12),
  `--model=linear|exponential|auto` (default `auto`), `--format=md|html|json`,
  `--out`, and `--fail-on-degrading` for CI pipelines. Exits `0` on success or
  `1` on argument/I/O error or a DEGRADING trend when the guard flag is set.
- 41 new unit tests (JUnit 5) covering OLS on `y = 2x + 1`, exponential fit on
  `y = 2 · e^{0.1x}`, degenerate regression inputs (n=0, n=1, constant x/y),
  loader ordering and same-day deduplication using real `Gson`, forecaster
  trend direction / burndown / WoW delta, reporter section presence, and CLI
  happy / `--fail-on-degrading` / invalid argument paths.

## [2026.3.8] - 2026-04-17

### Added
- **Weekly Digest Report** — new `Tools → Weekly Digest…` action and `WeeklyDigestCli` entry point
  that produce a management-ready digest of technical debt in either Markdown or HTML. The report
  contains Summary, Priority Breakdown, SLA Compliance, Top Hotspots, Sprint Recommendations, and
  an optional Week-over-Week Delta.
- `DigestPeriod` with rolling `last7` / `last14` / `last30` windows plus explicit ISO-week
  (`week:YYYY-Www`) selection. Deterministic `[startMillis, endMillis)` spans in UTC.
- `TechDebtItemSnapshot` and `DigestSnapshotStore` persist point-in-time inventory snapshots as
  pretty-printed JSON with atomic (`.tmp` + move) writes, enabling reliable WoW delta computation.
- `DigestDataCollector` assembles deterministic `DigestData` payloads with injected valuators and
  an `SlaSummaryProvider` hook — no IntelliJ dependencies, fully unit-testable.
- `MarkdownDigestRenderer` and `HtmlDigestRenderer` with pipe escaping, HTML entity escaping, and
  a self-contained dark-mode-aware stylesheet suitable for email delivery.
- CLI supports `--period`, `--format=md|html`, `--inventory`, `--previous-snapshot`,
  `--save-snapshot`, `--out`, `--project`, and `--hourly-rate` flags. Exits `0` on success, `1` on
  argument or I/O errors.
- 42 new unit tests (JUnit 5) covering period maths, snapshot round-trip, collector aggregations,
  WoW delta, renderer sections/escaping, and CLI argument handling.

## [2026.3.7] - 2026-04-17

### Added
- **Debt SLA Policy Engine** — define per-priority SLAs (HIGH = 30d, MEDIUM = 90d, LOW = 365d by
  default) and evaluate the inventory against them. Items are classified as OK / APPROACHING /
  BREACHED, breached HIGH items are auto-escalated to CRITICAL when the policy enables it, and a
  traffic-light Markdown digest is produced for weekly leadership reporting.
- Pure-Kotlin `SlaPolicy`, `SlaPolicyStore`, `SlaEvaluator`, `SlaEscalator`, `SlaMarkdownReporter`,
  and `SlaCli` — all with injected `Instant` clock for deterministic tests. Policy is persisted at
  `<projectRoot>/.debt-sla.json` with atomic-write semantics.
- New `Tools → Evaluate Debt SLAs…` action with a three-tab results dialog (Breached / Approaching /
  Escalations), Copy Markdown / Export .md / Open Policy Config buttons, and optional
  apply-escalations checkbox.
- New `Tools → Edit Debt SLA Policy` action that opens `.debt-sla.json` in the editor (creates it
  from defaults if missing).
- CLI entry point `com.jakubjirak.debtaccountant.sla.SlaCli` for CI pipelines with
  `--inventory`, `--policy`, `--output=md|json`, and `--fail-on-breach` flags (exits 2 on breach).
- Deterministic SHA-256 policy version hash included in every evaluation summary.
- 50 new unit tests covering validation, store round-trip, evaluator boundaries, markdown rendering,
  and CLI argument parsing.

## [2026.3.6] - 2026-04-17

### Added
- **Sprint Planning Recommender** — new `Tools → Suggest Sprint Items…` action that takes a time
  budget (hours), optional tag include/exclude filters, a minimum priority, and an overflow flag,
  then returns a greedy-knapsack sprint backlog maximizing ROI (monthly interest saved ÷ fix hours).
- Pure-Kotlin `SprintPlanningService` with deterministic sort (ROI desc, priority desc, age desc,
  id asc), overflow-once support (accepts one oversize top item within +20% of budget), and a
  Markdown renderer that escapes pipes/brackets/backticks.
- Result dialog with selectable items table, summary labels, **Copy Markdown** and **Export .md**
  controls wired to the real `DollarValuator` and `DebtInventoryStore`.
- 16 unit tests covering filtering, deterministic ordering, overflow bounds, totals, and markdown
  escaping.

## [2026.2.5] - 2026-02-14

### Fixed
- Removed deprecated API usage (postStartupActivity → projectActivity, StatusBarWidget.getPresentation)
- No experimental API usage


## [2026.2.4] - 2026-02-13

### Added
- Added ProGuard obfuscation for code protection


## [2026.2.3] - 2026-02-13

### Changed
- Version bump for JetBrains Marketplace publication


## [2026.2.2] - 2026-02-13

### Changed
- Expanded compatibility range: sinceBuild 232.0+ (IntelliJ 2023.2+)
- Removed upper version bound for future IDE compatibility


## [2026.2.1] - 2026-02-13

### Added
- Initial release
- Debt inventory with 9 categories (Code Smell, Missing Tests, Outdated Dependency, Security Vulnerability, Performance Issue, Documentation Gap, Architecture Violation, Hardcoded Value, Copy-Paste Code)
- 4 priority levels with weight multipliers (Critical 4×, High 3×, Medium 2×, Low 1×)
- Add Debt Item action with **Alt+D** keyboard shortcut
- Workaround time tracking with timer and manual logging
- Hotspot detection — identifies files with the most concentrated debt
- Git integration — links debt items to commits and file authors
- Gutter icons with priority-based coloring and debt tooltips
- Tech Debt tool window with sortable table and summary bar
- Project-level persistence in `.tech-debt/inventory.json`
- Settings panel (Tools → Technical Debt Accountant) for hourly rate, currency, inventory path, and gutter icons
- Startup activity to load inventory on project open
- Dollar valuation of workaround time (configurable hourly rate and currency)
- Monthly interest calculation over 30-day rolling windows
- ROI calculator with breakeven month estimation
- Burndown chart with trend detection (IMPROVING / STABLE / WORSENING)
- Management report generation (Markdown) with executive summary, hotspots, and ROI opportunities
- CSV export for burndown data
- Unlimited debt items
- Team aggregation across members and projects
- JIRA integration for linking debt items to tickets
- Organizational trend analytics
