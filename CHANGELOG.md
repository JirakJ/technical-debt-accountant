# Changelog

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
