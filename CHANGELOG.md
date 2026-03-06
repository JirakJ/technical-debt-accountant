# Changelog

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
